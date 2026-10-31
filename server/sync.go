package main

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"errors"
	"net/http"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/pocketbase/dbx"
	"github.com/pocketbase/pocketbase/apis"
	"github.com/pocketbase/pocketbase/core"
)

/*
 Cloud sync: a signed-in user may keep their reminders and notes on the server, so a new phone gets them back.
 The phone encrypts every item before sending it, with a key the server works out from its own secret and the
 user's id (the key is never stored), so the stored rows alone cannot be read. Each row is one item; the phone sends
 only what changed and asks only for what changed since its last visit.
*/

const (
	syncMaxItems   = 20000  // per user
	syncMaxPerCall = 500    // items in one request
	syncMaxData    = 200000 // bytes of one item
)

var (
	secretOnce sync.Once
	secretKey  []byte
)

// serverSecret is SYNC_SECRET, else OTP_SECRET, else a random secret kept in the database (made once).
// Changing it makes the cloud copies unreadable, so it must stay the same.
func serverSecret(app core.App) []byte {
	secretOnce.Do(func() {
		for _, env := range []string{"SYNC_SECRET", "OTP_SECRET"} {
			if v := strings.TrimSpace(os.Getenv(env)); len(v) >= 8 {
				secretKey = []byte(v)
				return
			}
		}
		col, err := ensureCollection(app, "secrets", func(c *core.Collection) {
			addField(c, &core.TextField{Name: "name", Max: 40, Presentable: true})
			addField(c, &core.TextField{Name: "value", Max: 200, Hidden: true})
		})
		if err != nil {
			secretKey = []byte("yadimo-fallback-secret")
			return
		}
		if r, _ := app.FindFirstRecordByFilter("secrets", "name = 'sync'"); r != nil {
			secretKey = []byte(r.GetString("value"))
			return
		}
		b := make([]byte, 32)
		_, _ = rand.Read(b)
		r := core.NewRecord(col)
		r.Set("name", "sync")
		r.Set("value", base64.StdEncoding.EncodeToString(b))
		_ = app.Save(r)
		secretKey = []byte(r.GetString("value"))
	})
	return secretKey
}

// deriveKey gives a 32-byte key for one purpose ("user|<id>", "group|<id>").
func deriveKey(secret []byte, purpose string) []byte {
	m := hmac.New(sha256.New, secret)
	m.Write([]byte("yadimo-sync|" + purpose))
	return m.Sum(nil)
}

// seal and unseal are AES-GCM with a random nonce in front; the text is base64.
func seal(key []byte, plain string) (string, error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	nonce := make([]byte, gcm.NonceSize())
	if _, err := rand.Read(nonce); err != nil {
		return "", err
	}
	return base64.StdEncoding.EncodeToString(gcm.Seal(nonce, nonce, []byte(plain), nil)), nil
}

func unseal(key []byte, sealed string) (string, error) {
	raw, err := base64.StdEncoding.DecodeString(sealed)
	if err != nil {
		return "", err
	}
	block, err := aes.NewCipher(key)
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	if len(raw) < gcm.NonceSize() {
		return "", errors.New("short")
	}
	plain, err := gcm.Open(nil, raw[:gcm.NonceSize()], raw[gcm.NonceSize():], nil)
	return string(plain), err
}

func nowMs() int64 { return time.Now().UnixMilli() }

// handleSyncKey gives the phone the key it encrypts this user's items with.
func handleSyncKey(e *core.RequestEvent) error {
	key := deriveKey(serverSecret(e.App), "user|"+e.Auth.Id)
	return e.JSON(http.StatusOK, map[string]any{"key": base64.StdEncoding.EncodeToString(key)})
}

type syncItem struct {
	UID     string `json:"uid"`
	Kind    string `json:"kind"`
	Data    string `json:"data"`
	Deleted bool   `json:"deleted"`
	Updated int64  `json:"updated"` // when the phone changed it (ms)
}

// handleSyncPull returns the items changed since ?since= (ms, server time), and the server time to ask from next.
func handleSyncPull(e *core.RequestEvent) error {
	since, _ := strconv.ParseInt(e.Request.URL.Query().Get("since"), 10, 64)
	now := nowMs()
	// a little overlap, so a row written in the same moment is never missed (the phone merges by "updated")
	records, err := e.App.FindRecordsByFilter("sync_items", "user = {:u} && at >= {:s}", "at", 5000, 0,
		dbx.Params{"u": e.Auth.Id, "s": since - 5000})
	if err != nil {
		return err
	}
	items := make([]syncItem, 0, len(records))
	for _, r := range records {
		items = append(items, syncItem{UID: r.GetString("uid"), Kind: r.GetString("kind"), Data: r.GetString("data"),
			Deleted: r.GetBool("deleted"), Updated: int64(r.GetFloat("updated"))})
	}
	more := len(records) == 5000
	next := now
	if more {
		next = int64(records[len(records)-1].GetFloat("at"))
	}
	return e.JSON(http.StatusOK, map[string]any{"now": next, "more": more, "items": items})
}

// handleSyncPush stores what the phone changed; an older change never overwrites a newer one.
func handleSyncPush(e *core.RequestEvent) error {
	var body struct {
		Items []syncItem `json:"items"`
	}
	if err := e.BindBody(&body); err != nil || len(body.Items) > syncMaxPerCall {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	col, err := e.App.FindCollectionByNameOrId("sync_items")
	if err != nil {
		return err
	}
	count, _ := e.App.CountRecords("sync_items", dbx.NewExp("user = {:u}", dbx.Params{"u": e.Auth.Id}))
	saved := 0
	for _, it := range body.Items {
		if it.UID == "" || len(it.UID) > 64 || len(it.Data) > syncMaxData || len(it.Kind) > 20 {
			continue
		}
		r, _ := e.App.FindFirstRecordByFilter("sync_items", "user = {:u} && uid = {:id}", dbx.Params{"u": e.Auth.Id, "id": it.UID})
		if r == nil {
			if count >= syncMaxItems {
				continue
			}
			r = core.NewRecord(col)
			r.Set("user", e.Auth.Id)
			r.Set("uid", it.UID)
			count++
		} else if int64(r.GetFloat("updated")) > it.Updated {
			continue
		}
		r.Set("kind", it.Kind)
		if it.Deleted {
			r.Set("data", "")
		} else {
			r.Set("data", it.Data)
		}
		r.Set("deleted", it.Deleted)
		r.Set("updated", it.Updated)
		r.Set("at", nowMs())
		if err := e.App.Save(r); err != nil {
			return err
		}
		saved++
	}
	return e.JSON(http.StatusOK, map[string]any{"saved": saved, "now": nowMs()})
}

// handleSyncWipe removes everything this user keeps in the cloud (when they turn sync off and ask for it).
func handleSyncWipe(e *core.RequestEvent) error {
	_, err := e.App.DB().NewQuery("DELETE FROM sync_items WHERE user = {:u}").Bind(dbx.Params{"u": e.Auth.Id}).Execute()
	if err != nil {
		return err
	}
	return e.JSON(http.StatusOK, map[string]any{"ok": true})
}
