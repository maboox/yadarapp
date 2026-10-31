package main

import (
	"net/http"
	"regexp"
	"strings"

	"github.com/pocketbase/dbx"
	"github.com/pocketbase/pocketbase/apis"
	"github.com/pocketbase/pocketbase/core"
)

// Usernames: the @name people find each other by (for groups later). English letters, digits and "_", 3–20
// characters, starting with a letter; stored in lower case, so "Amin" and "amin" are the same name. The database
// keeps them unique (idx_users_username), so two people can never end up with one name.

var usernamePattern = regexp.MustCompile(`^[a-z][a-z0-9_]{2,19}$`)

// Words no username may contain, and names that are taken as a whole.
var usernameNever = []string{"admin", "yadimo", "yadar", "support", "poshtiban", "modir", "moderator", "official", "staff"}
var usernameTaken = map[string]bool{
	"god": true, "khoda": true, "khodaa": true, "allah": true, "kami": true, "kamisama": true, "kami_sama": true,
	"root": true, "system": true, "help": true, "info": true, "bazaar": true, "cafebazaar": true, "null": true, "owner": true,
}

// checkUsername returns the username as stored, or why it cannot be used.
func checkUsername(raw string) (string, string) {
	u := strings.ToLower(strings.TrimSpace(strings.TrimPrefix(strings.TrimSpace(raw), "@")))
	if !usernamePattern.MatchString(u) {
		return "", "فقط حروف انگلیسی، عدد و _ ؛ ۳ تا ۲۰ حرف و با یک حرف شروع شود"
	}
	for _, w := range usernameNever {
		if strings.Contains(strings.ReplaceAll(u, "_", ""), w) {
			return "", "این نام رزرو شده است"
		}
	}
	if usernameTaken[u] || usernameTaken[strings.ReplaceAll(u, "_", "")] {
		return "", "این نام رزرو شده است"
	}
	return u, ""
}

// Words a display name may not be, so nobody passes for the team (whole words; a few also inside a word).
var nameNever = []string{"ادمین", "پشتیبانی", "یادیمو", "admin", "support", "yadimo"}
var nameWords = map[string]bool{"مدیر": true, "خدا": true, "خداوند": true, "کامی": true, "کامیساما": true, "god": true, "allah": true,
	"kami": true, "kamisama": true, "moderator": true, "الله": true}

// nameProblem tells why a display name cannot be used, or "".
func nameProblem(name string) string {
	n := strings.ToLower(name)
	n = strings.NewReplacer("ي", "ی", "ك", "ک", "‌", "", "‌", "", "-", " ", "_", " ", ".", " ").Replace(n)
	flat := strings.ReplaceAll(n, " ", "")
	for _, w := range nameNever {
		if strings.Contains(flat, w) {
			return "این نام رزرو شده است"
		}
	}
	if nameWords[flat] {
		return "این نام رزرو شده است"
	}
	for _, w := range strings.Fields(n) {
		if nameWords[w] {
			return "این نام رزرو شده است"
		}
	}
	return ""
}

func usernameFree(app core.App, u, self string) bool {
	rec, _ := app.FindFirstRecordByFilter("users", "username = {:u} && id != {:me}", dbx.Params{"u": u, "me": self})
	return rec == nil
}

// handleUsernameCheck answers while the user types: {ok} or {ok: false, reason}.
func handleUsernameCheck(e *core.RequestEvent) error {
	u, why := checkUsername(e.Request.URL.Query().Get("u"))
	if why == "" && !usernameFree(e.App, u, e.Auth.Id) {
		why = "این نام را کس دیگری برداشته"
	}
	return e.JSON(http.StatusOK, map[string]any{"ok": why == "", "reason": why, "username": u})
}

// setUsername checks and stores a new username on [user] (not saved yet).
func setUsername(app core.App, user *core.Record, raw string) error {
	if strings.TrimSpace(raw) == "" {
		user.Set("username", "")
		return nil
	}
	u, why := checkUsername(raw)
	if why == "" && !usernameFree(app, u, user.Id) {
		why = "این نام را کس دیگری برداشته"
	}
	if why != "" {
		return apis.NewBadRequestError(why, nil)
	}
	user.Set("username", u)
	return nil
}
