package main

import (
	"net/http"
	"unicode/utf8"

	"github.com/pocketbase/pocketbase/apis"
	"github.com/pocketbase/pocketbase/core"
)

// handleStats keeps one small totals row per user (see the user_stats collection). It never receives titles,
// notes or any other content, only counts.
func handleStats(e *core.RequestEvent) error {
	var body struct {
		Reminders  float64 `json:"reminders"`
		Notes      float64 `json:"notes"`
		Done       float64 `json:"done"`
		Missed     float64 `json:"missed"`
		AIRequests float64 `json:"ai_requests"`
		DaysUsed   float64 `json:"days_used"`
		Stars      float64 `json:"stars"`
		Version    string  `json:"version"`
		Language   string  `json:"language"`
		Theme      string  `json:"theme"`
	}
	if err := e.BindBody(&body); err != nil {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	clamp := func(v, hi float64) float64 {
		if v < 0 {
			return 0
		}
		if v > hi {
			return hi
		}
		return float64(int64(v))
	}
	short := func(s string, n int) string {
		if utf8.RuneCountInString(s) > n {
			return string([]rune(s)[:n])
		}
		return s
	}
	col, err := e.App.FindCollectionByNameOrId("user_stats")
	if err != nil {
		return err
	}
	rec, _ := e.App.FindFirstRecordByData("user_stats", "user", e.Auth.Id)
	if rec == nil {
		rec = core.NewRecord(col)
		rec.Set("user", e.Auth.Id)
	}
	rec.Set("reminders", clamp(body.Reminders, 100000))
	rec.Set("notes", clamp(body.Notes, 100000))
	rec.Set("done", clamp(body.Done, 1000000))
	rec.Set("missed", clamp(body.Missed, 1000000))
	rec.Set("ai_requests", clamp(body.AIRequests, 100000))
	rec.Set("days_used", clamp(body.DaysUsed, 100000))
	rec.Set("stars", clamp(body.Stars, 5))
	rec.Set("version", short(body.Version, 20))
	rec.Set("language", short(body.Language, 10))
	rec.Set("theme", short(body.Theme, 20))
	if err := e.App.Save(rec); err != nil {
		return err
	}
	return e.JSON(http.StatusOK, map[string]any{"ok": true})
}
