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
	g.GET("/bazaar/start", handleBazaarStart)
	g.GET("/bazaar/callback", handleBazaarCallback)
	g.POST("/bazaar/claim", handleBazaarClaim)
	g.POST("/bazaar/app-login", handleBazaarAppLogin)
	g.GET("/weather", handleWeather)
	g.GET("/announcements", handleAnnouncements)

	auth := g.Group("")
	auth.Bind(apis.RequireAuth("users"))
	auth.GET("/me", func(e *core.RequestEvent) error {
		return e.JSON(http.StatusOK, profile(e.App, e.Auth))
	})
	auth.POST("/me", handleUpdateProfile)
	auth.GET("/username/check", handleUsernameCheck)
	auth.GET("/sync/key", handleSyncKey)
	auth.GET("/sync", handleSyncPull)
	auth.POST("/sync", handleSyncPush)
	auth.POST("/sync/wipe", handleSyncWipe)
	auth.GET("/groups", handleGroupsSync)
	auth.POST("/groups", handleGroupCreate)
	auth.POST("/groups/{id}/settings", handleGroupSettings)
	auth.POST("/groups/{id}/invite", handleGroupInvite)
	auth.POST("/groups/{id}/accept", handleGroupAnswer(true))
	auth.POST("/groups/{id}/decline", handleGroupAnswer(false))
	auth.POST("/groups/{id}/leave", handleGroupLeave)
	auth.POST("/groups/{id}/remove", handleGroupRemove)
	auth.POST("/groups/{id}/delete", handleGroupDelete)
	auth.POST("/groups/{id}/items", handleGroupItem)
	auth.POST("/groups/{id}/done", handleGroupDone)
	auth.GET("/usage", handleUsage)
	auth.POST("/ai/chat", handleChat)
	auth.POST("/ai/transcribe", handleTranscribe)
	auth.GET("/packs", handlePacks)
	auth.POST("/stats", handleStats)
	auth.POST("/purchase", handlePurchase)
	auth.POST("/purchase/consumed", handlePurchaseConsumed)
	auth.GET("/yadimo", handleYadimo)
	auth.POST("/yadimo/buy", handleYadimoBuy)
	auth.POST("/yadimo/look", handleYadimoLook)
	auth.GET("/game", handleGame)
	auth.POST("/game", handleGameSave)
	auth.POST("/game/claim", handleGameClaim)
	auth.POST("/announcements", handleAnnounceCreate)
	auth.GET("/announcements/all", handleAnnounceList)
	auth.POST("/announcements/{id}/off", handleAnnounceOff)
	auth.POST("/announcements/audience", handleAudience)
	auth.GET("/admin/users", handleAdminUsers)
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
		"bazaar":      user.GetString("bazaar_id") != "",
		"admin":       user.GetBool("is_admin"),
		"name":        user.GetString("name"),
		"username":    user.GetString("username"),
		"purchased":   purchasedTokens(app, user.Id),
		"balance":     user.GetFloat("balance"),
		"unlimited":   user.GetBool("unlimited"),
		"plan":        planName,
		"text_model":  effectiveModel(app, user, false, s),
		"audio_model": effectiveModel(app, user, true, s),
		"ai_enabled":  s.AIEnabled,
	}
}

func handleUpdateProfile(e *core.RequestEvent) error {
	// only what is sent changes
	var body struct {
		Name     *string `json:"name"`
		Username *string `json:"username"`
	}
	if err := e.BindBody(&body); err != nil {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	user, err := e.App.FindRecordById("users", e.Auth.Id)
	if err != nil {
		return err
	}
	if body.Name != nil {
		name := strings.TrimSpace(*body.Name)
		if utf8.RuneCountInString(name) > 60 {
			return apis.NewBadRequestError("نام خیلی بلند است", nil)
		}
		if why := nameProblem(name); why != "" && !user.GetBool("is_admin") {
			return apis.NewBadRequestError(why, nil)
		}
		user.Set("name", name)
	}
	if body.Username != nil {
		if err := setUsername(e.App, user, *body.Username); err != nil {
			return err
		}
	}
	if err := e.App.Save(user); err != nil {
		// two people chose the same name at the same moment: the database lets only one have it
		if body.Username != nil && strings.Contains(strings.ToLower(err.Error()), "unique") {
			return apis.NewBadRequestError("این نام را کس دیگری برداشته", nil)
		}
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
