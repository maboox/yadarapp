package main

import (
	"net/http"
	"strings"
	"unicode/utf8"

	"github.com/pocketbase/dbx"
	"github.com/pocketbase/pocketbase/apis"
	"github.com/pocketbase/pocketbase/core"
)

func registerRoutes(se *core.ServeEvent) {
	g := se.Router.Group("/api/yadar")
	g.GET("/health", func(e *core.RequestEvent) error {
		return e.JSON(http.StatusOK, map[string]any{"ok": true})
	})
	g.POST("/otp/request", handleOTPRequest)
	g.POST("/otp/verify", handleOTPVerify)

	auth := g.Group("")
	auth.Bind(apis.RequireAuth("users"))
	auth.GET("/me", func(e *core.RequestEvent) error {
		return e.JSON(http.StatusOK, profile(e.App, e.Auth))
	})
	auth.POST("/me", handleUpdateProfile)
	auth.GET("/usage", handleUsage)
	auth.POST("/ai/chat", handleChat)
	auth.POST("/ai/transcribe", handleTranscribe)
}

// profile is what the app shows on the account screen.
func profile(app core.App, user *core.Record) map[string]any {
	s := loadSettings(app)
	planName := ""
	if id := user.GetString("plan"); id != "" {
		if plan, _ := app.FindRecordById("plans", id); plan != nil {
			planName = plan.GetString("name")
		}
	}
	return map[string]any{
		"id":          user.Id,
		"phone":       user.GetString("phone"),
		"name":        user.GetString("name"),
		"balance":     user.GetFloat("balance"),
		"unlimited":   user.GetBool("unlimited"),
		"plan":        planName,
		"text_model":  effectiveModel(app, user, false, s),
		"audio_model": effectiveModel(app, user, true, s),
		"ai_enabled":  s.AIEnabled,
	}
}

func handleUpdateProfile(e *core.RequestEvent) error {
	var body struct {
		Name string `json:"name"`
	}
	if err := e.BindBody(&body); err != nil {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	name := strings.TrimSpace(body.Name)
	if utf8.RuneCountInString(name) > 60 {
		return apis.NewBadRequestError("نام خیلی بلند است", nil)
	}
	user, err := e.App.FindRecordById("users", e.Auth.Id)
	if err != nil {
		return err
	}
	user.Set("name", name)
	if err := e.App.Save(user); err != nil {
		return err
	}
	return e.JSON(http.StatusOK, profile(e.App, user))
}

func handleUsage(e *core.RequestEvent) error {
	records, err := e.App.FindRecordsByFilter("usage", "user = {:u}", "-created", 30, 0, dbx.Params{"u": e.Auth.Id})
	if err != nil {
		return err
	}
	items := make([]map[string]any, 0, len(records))
	for _, r := range records {
		items = append(items, map[string]any{
			"kind": r.GetString("kind"), "tokens": r.GetFloat("tokens"), "created": r.GetDateTime("created").Time().UnixMilli(),
		})
	}
	return e.JSON(http.StatusOK, map[string]any{"items": items})
}

func registerHooks(app core.App) {
	// Admin panel: tick "check_now" on a provider and save to see which configured models it offers.
	check := func(e *core.RecordRequestEvent) error {
		if e.Record.GetBool("check_now") {
			e.Record.Set("check_result", checkProvider(e.App, e.Record))
			e.Record.Set("check_now", false)
		}
		return e.Next()
	}
	app.OnRecordCreateRequest("providers").BindFunc(check)
	app.OnRecordUpdateRequest("providers").BindFunc(check)
}
