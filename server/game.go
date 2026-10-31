package main

import (
	"encoding/json"
	"math"
	"net/http"

	"github.com/pocketbase/dbx"
	"github.com/pocketbase/pocketbase/apis"
	"github.com/pocketbase/pocketbase/core"
)

// Progress (points, level, badges, missions) is worked out by the app from the reminders on the phone and kept
// here so it comes back after signing in on another phone. Only the few starter missions that give tokens are
// decided here: each pays once per account and once per phone, and they add up to 10 tokens in total.

var missionTokens = map[string]float64{"voice3": 3, "done10": 2, "streak7": 5}

type gameState struct {
	XP       int      `json:"xp"`
	Level    int      `json:"level"`
	Badges   []string `json:"badges"`
	Missions []string `json:"missions"`
	Streak   int      `json:"streak"`
	Done     int      `json:"done"`
}

func loadGame(user *core.Record) gameState {
	var g gameState
	_ = json.Unmarshal([]byte(user.GetString("game")), &g)
	return g
}

// gameLevel is the level that counts: the one set by hand in the admin panel, otherwise the app's.
func gameLevel(user *core.Record) int {
	if v := int(user.GetFloat("level_override")); v > 0 {
		return v
	}
	return loadGame(user).Level
}

// purchasedTokens is how many tokens the user has bought in all (it decides the ring around their picture).
func purchasedTokens(app core.App, userID string) float64 {
	var row struct {
		Total float64 `db:"total"`
	}
	_ = app.DB().NewQuery("SELECT COALESCE(SUM(tokens), 0) AS total FROM purchases WHERE user = {:u}").Bind(dbx.Params{"u": userID}).One(&row)
	return row.Total
}

func union(a, b []string) []string {
	seen := map[string]bool{}
	out := []string{}
	for _, list := range [][]string{a, b} {
		for _, v := range list {
			if v != "" && len(v) <= 40 && !seen[v] && len(out) < 200 {
				seen[v] = true
				out = append(out, v)
			}
		}
	}
	return out
}

func gameAnswer(app core.App, user *core.Record) map[string]any {
	claimed := []string{}
	if records, err := app.FindRecordsByFilter("rewards", "user = {:u}", "", 50, 0, dbx.Params{"u": user.Id}); err == nil {
		for _, r := range records {
			claimed = append(claimed, r.GetString("mission"))
		}
	}
	return map[string]any{"game": loadGame(user), "level_override": int(user.GetFloat("level_override")), "claimed": claimed,
		"rewards": missionTokens, "balance": user.GetFloat("balance")}
}

func handleGame(e *core.RequestEvent) error {
	user, err := e.App.FindRecordById("users", e.Auth.Id)
	if err != nil {
		return err
	}
	return e.JSON(http.StatusOK, gameAnswer(e.App, user))
}

// handleGameSave keeps the larger of what the server has and what the app sends, so progress never goes back.
func handleGameSave(e *core.RequestEvent) error {
	var body gameState
	if err := e.BindBody(&body); err != nil {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	user, err := e.App.FindRecordById("users", e.Auth.Id)
	if err != nil {
		return err
	}
	g := loadGame(user)
	clamp := func(v, hi int) int { return int(math.Max(0, math.Min(float64(v), float64(hi)))) }
	g.XP = max(g.XP, clamp(body.XP, 10_000_000))
	g.Level = max(g.Level, clamp(body.Level, 100))
	g.Streak = max(g.Streak, clamp(body.Streak, 100_000))
	g.Done = max(g.Done, clamp(body.Done, 10_000_000))
	g.Badges = union(g.Badges, body.Badges)
	g.Missions = union(g.Missions, body.Missions)
	raw, _ := json.Marshal(g)
	user.Set("game", string(raw))
	if err := e.App.Save(user); err != nil {
		return err
	}
	return e.JSON(http.StatusOK, gameAnswer(e.App, user))
}

// handleGameClaim pays a starter mission's tokens once per account and once per phone.
func handleGameClaim(e *core.RequestEvent) error {
	var body struct {
		Mission string `json:"mission"`
		Device  string `json:"device"`
	}
	if err := e.BindBody(&body); err != nil {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	tokens, ok := missionTokens[body.Mission]
	device := deviceHash(body.Device)
	if !ok || device == "" {
		return apis.NewBadRequestError("این مأموریت شناخته نشد", nil)
	}
	app := e.App
	user, err := app.FindRecordById("users", e.Auth.Id)
	if err != nil {
		return err
	}
	g := loadGame(user)
	done := false
	switch body.Mission {
	case "voice3":
		// Voice requests are logged here, so this one is checked for real.
		n, _ := app.CountRecords("usage", dbx.NewExp("user = {:u} AND kind = 'voice'", dbx.Params{"u": user.Id}))
		done = n >= 3
	case "done10":
		done = g.Done >= 10
	case "streak7":
		done = g.Streak >= 7
	}
	if !done {
		return apis.NewBadRequestError("این مأموریت هنوز کامل نشده", nil)
	}
	err = app.RunInTransaction(func(tx core.App) error {
		if r, _ := tx.FindFirstRecordByFilter("rewards", "user = {:u} && mission = {:m}", dbx.Params{"u": user.Id, "m": body.Mission}); r != nil {
			return apis.NewBadRequestError("جایزهٔ این مأموریت را قبلاً گرفته‌ای", nil)
		}
		if r, _ := tx.FindFirstRecordByFilter("rewards", "device = {:d} && mission = {:m}", dbx.Params{"d": device, "m": body.Mission}); r != nil {
			return apis.NewBadRequestError("جایزهٔ این مأموریت روی این گوشی قبلاً گرفته شده", nil)
		}
		col, err := tx.FindCollectionByNameOrId("rewards")
		if err != nil {
			return err
		}
		r := core.NewRecord(col)
		r.Set("user", user.Id)
		r.Set("mission", body.Mission)
		r.Set("device", device)
		r.Set("tokens", tokens)
		if err := tx.Save(r); err != nil {
			return err
		}
		u, err := tx.FindRecordById("users", user.Id)
		if err != nil {
			return err
		}
		u.Set("balance", math.Round((u.GetFloat("balance")+tokens)*10)/10)
		user = u
		return tx.Save(u)
	})
	if err != nil {
		return err
	}
	answer := gameAnswer(app, user)
	answer["added"] = tokens
	answer["profile"] = profile(app, user)
	return e.JSON(http.StatusOK, answer)
}
