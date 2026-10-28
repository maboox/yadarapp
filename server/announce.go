package main

import (
	"net/http"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/pocketbase/dbx"
	"github.com/pocketbase/pocketbase/apis"
	"github.com/pocketbase/pocketbase/core"
)

// Messages to every user (a new version, holiday greetings…). The app asks for new ones when it opens and every
// few hours: one small request that is usually answered with an empty list, so it costs the server nothing.
// Only users ticked as admin in the panel can send them from the app; the panel can edit or switch them off too.

var announceStyles = map[string]bool{"popup": true, "notification": true, "fullscreen": true}

func announceJSON(r *core.Record) map[string]any {
	ends := int64(0)
	if d := r.GetDateTime("ends"); !d.IsZero() {
		ends = d.Time().UnixMilli()
	}
	return map[string]any{
		"id": r.Id, "title": r.GetString("title"), "body": r.GetString("body"), "style": r.GetString("style"),
		"button_text": r.GetString("button_text"), "button_url": r.GetString("button_url"),
		"below_version": r.GetInt("below_version"), "ends": ends, "active": r.GetBool("active"), "target": r.Get("target"),
		"created": r.GetDateTime("created").Time().UnixMilli(),
	}
}

// handleAnnouncements lists the active messages sent after ?since= (milliseconds).
func handleAnnouncements(e *core.RequestEvent) error {
	since, _ := strconv.ParseInt(e.Request.URL.Query().Get("since"), 10, 64)
	if since < 0 {
		since = 0
	}
	// Never older than 30 days: a new install should not get a pile of old news.
	if floor := time.Now().Add(-30 * 24 * time.Hour).UnixMilli(); since < floor {
		since = floor
	}
	after := time.UnixMilli(since).UTC().Format("2006-01-02 15:04:05.000Z")
	now := time.Now().UTC().Format("2006-01-02 15:04:05.000Z")
	records, err := e.App.FindRecordsByFilter("announcements",
		"active = true && created > {:after} && (ends = '' || ends > {:now})", "created", 10, 0,
		dbx.Params{"after": after, "now": now})
	if err != nil {
		return err
	}
	items := make([]map[string]any, 0, len(records))
	for _, r := range records {
		items = append(items, announceJSON(r))
	}
	e.Response.Header().Set("Cache-Control", "public, max-age=300")
	return e.JSON(http.StatusOK, map[string]any{"items": items, "now": time.Now().UnixMilli()})
}

func requireAdmin(e *core.RequestEvent) (*core.Record, error) {
	user, err := e.App.FindRecordById("users", e.Auth.Id)
	if err != nil {
		return nil, err
	}
	if !user.GetBool("is_admin") {
		return nil, apis.NewForbiddenError("فقط مدیر", nil)
	}
	return user, nil
}

// handleAnnounceCreate sends a message to everyone (admins only).
func handleAnnounceCreate(e *core.RequestEvent) error {
	if _, err := requireAdmin(e); err != nil {
		return err
	}
	var body struct {
		Title        string `json:"title"`
		Body         string `json:"body"`
		Style        string `json:"style"`
		ButtonText   string `json:"button_text"`
		ButtonURL    string `json:"button_url"`
		BelowVersion int    `json:"below_version"`
		Days         int    `json:"days"`
		Target       target `json:"target"`
	}
	if err := e.BindBody(&body); err != nil {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	body.Title, body.Body = strings.TrimSpace(body.Title), strings.TrimSpace(body.Body)
	if body.Body == "" || utf8.RuneCountInString(body.Title) > 80 || utf8.RuneCountInString(body.Body) > 1000 ||
		utf8.RuneCountInString(body.ButtonText) > 30 || len(body.ButtonURL) > 300 {
		return apis.NewBadRequestError("متن پیام خالی یا خیلی بلند است", nil)
	}
	if !announceStyles[body.Style] {
		body.Style = "popup"
	}
	if u := body.ButtonURL; u != "" && !(strings.HasPrefix(u, "https://") || strings.HasPrefix(u, "bazaar://") || strings.HasPrefix(u, "yadimo://")) {
		return apis.NewBadRequestError("لینک دکمه باید با https:// یا bazaar:// شروع شود", nil)
	}
	col, err := e.App.FindCollectionByNameOrId("announcements")
	if err != nil {
		return err
	}
	r := core.NewRecord(col)
	r.Set("title", body.Title)
	r.Set("body", body.Body)
	r.Set("style", body.Style)
	r.Set("button_text", strings.TrimSpace(body.ButtonText))
	r.Set("button_url", body.ButtonURL)
	r.Set("below_version", body.BelowVersion)
	if t := body.Target.clean(); !t.empty() {
		r.Set("target", t)
	}
	if body.Days > 0 && body.Days <= 365 {
		r.Set("ends", time.Now().Add(time.Duration(body.Days)*24*time.Hour))
	}
	r.Set("active", true)
	if err := e.App.Save(r); err != nil {
		return err
	}
	return e.JSON(http.StatusOK, announceJSON(r))
}

// handleAnnounceList shows the admin the latest messages, including switched-off ones.
func handleAnnounceList(e *core.RequestEvent) error {
	if _, err := requireAdmin(e); err != nil {
		return err
	}
	records, err := e.App.FindRecordsByFilter("announcements", "id != ''", "-created", 20, 0)
	if err != nil {
		return err
	}
	items := make([]map[string]any, 0, len(records))
	for _, r := range records {
		items = append(items, announceJSON(r))
	}
	return e.JSON(http.StatusOK, map[string]any{"items": items})
}

// handleAnnounceOff stops a message from being shown to anyone who has not seen it yet.
func handleAnnounceOff(e *core.RequestEvent) error {
	if _, err := requireAdmin(e); err != nil {
		return err
	}
	r, err := e.App.FindRecordById("announcements", e.Request.PathValue("id"))
	if err != nil {
		return apis.NewNotFoundError("پیدا نشد", nil)
	}
	r.Set("active", false)
	if err := e.App.Save(r); err != nil {
		return err
	}
	return e.JSON(http.StatusOK, announceJSON(r))
}

// target says who a message is for; the app checks it against itself, so the list endpoint stays public and cheap.
// Every condition must hold; a list of users, when given, is the whole audience.
type target struct {
	Logged     string   `json:"logged,omitempty"`      // "in" or "out"
	MinVersion int      `json:"min_version,omitempty"` // version code, inclusive
	MaxVersion int      `json:"max_version,omitempty"` // version code, exclusive
	Usage      string   `json:"usage,omitempty"`       // "active", "light" or "inactive"
	Users      []string `json:"users,omitempty"`
}

func (t target) clean() target {
	if t.Logged != "in" && t.Logged != "out" {
		t.Logged = ""
	}
	if t.Usage != "active" && t.Usage != "light" && t.Usage != "inactive" {
		t.Usage = ""
	}
	if len(t.Users) > 500 {
		t.Users = t.Users[:500]
	}
	return t
}

func (t target) empty() bool {
	return t.Logged == "" && t.MinVersion == 0 && t.MaxVersion == 0 && t.Usage == "" && len(t.Users) == 0
}

// handleAudience estimates how many signed-in users a target reaches (people who never signed in are not known).
func handleAudience(e *core.RequestEvent) error {
	if _, err := requireAdmin(e); err != nil {
		return err
	}
	var t target
	if err := e.BindBody(&t); err != nil {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	t = t.clean()
	if len(t.Users) > 0 {
		return e.JSON(http.StatusOK, map[string]any{"count": len(t.Users), "known": true})
	}
	if t.Logged == "out" {
		return e.JSON(http.StatusOK, map[string]any{"count": 0, "known": false})
	}
	where, params := statsFilter(t)
	n, err := e.App.CountRecords("user_stats", dbx.NewExp(where, params))
	if err != nil {
		return err
	}
	return e.JSON(http.StatusOK, map[string]any{"count": n, "known": true})
}

func statsFilter(t target) (string, dbx.Params) {
	where, params := "1=1", dbx.Params{}
	if t.MinVersion > 0 {
		where += " AND version_code >= {:minv}"
		params["minv"] = t.MinVersion
	}
	if t.MaxVersion > 0 {
		where += " AND version_code > 0 AND version_code < {:maxv}"
		params["maxv"] = t.MaxVersion
	}
	week := time.Now().Add(-7 * 24 * time.Hour).UTC().Format("2006-01-02 15:04:05.000Z")
	twoWeeks := time.Now().Add(-14 * 24 * time.Hour).UTC().Format("2006-01-02 15:04:05.000Z")
	switch t.Usage {
	case "active":
		where += " AND days_used >= 7 AND updated >= {:week}"
		params["week"] = week
	case "light":
		where += " AND days_used < 7"
	case "inactive":
		where += " AND updated < {:old}"
		params["old"] = twoWeeks
	}
	return where, params
}

// handleAdminUsers finds signed-in users by name or phone, with a few figures about their use of the app.
func handleAdminUsers(e *core.RequestEvent) error {
	if _, err := requireAdmin(e); err != nil {
		return err
	}
	q := strings.TrimSpace(e.Request.URL.Query().Get("q"))
	filter, params := "id != ''", dbx.Params{}
	if q != "" {
		filter = "name ~ {:q} || phone ~ {:q} || id = {:q}"
		params["q"] = q
	}
	users, err := e.App.FindRecordsByFilter("users", filter, "-updated", 30, 0, params)
	if err != nil {
		return err
	}
	items := make([]map[string]any, 0, len(users))
	for _, u := range users {
		item := map[string]any{"id": u.Id, "name": u.GetString("name"), "phone": u.GetString("phone")}
		if s, _ := e.App.FindFirstRecordByData("user_stats", "user", u.Id); s != nil {
			item["version"] = s.GetString("version")
			item["days_used"] = s.GetInt("days_used")
			item["done"] = s.GetInt("done")
			item["reminders"] = s.GetInt("reminders")
			item["last"] = s.GetDateTime("updated").Time().UnixMilli()
		}
		items = append(items, item)
	}
	return e.JSON(http.StatusOK, map[string]any{"items": items})
}
