// Yadar server: phone login, token balances and an AI proxy in front of OpenAI-compatible providers
// (Liara, AvalAI, OpenRouter…). Built on PocketBase, whose admin panel (/_/) manages users, plans,
// models, providers and settings.
package main

import (
	"log"
	"os"
	"strings"

	"github.com/pocketbase/pocketbase"
	"github.com/pocketbase/pocketbase/core"
	"github.com/pocketbase/pocketbase/plugins/migratecmd"

	// Embedded root certificates: the image needs no OS certificate store (FROM scratch).
	_ "golang.org/x/crypto/x509roots/fallback"
)

func main() {
	app := pocketbase.New()
	migratecmd.MustRegister(app, app.RootCmd, migratecmd.Config{Automigrate: false})

	app.OnServe().BindFunc(func(se *core.ServeEvent) error {
		if err := ensureSchema(se.App); err != nil {
			return err
		}
		if err := configureApp(se.App); err != nil {
			return err
		}
		if err := ensureAdmin(se.App); err != nil {
			log.Println("admin setup:", err)
		}
		registerRoutes(se)
		return se.Next()
	})

	registerHooks(app)

	if err := app.Start(); err != nil {
		log.Fatal(err)
	}
}

// configureApp applies the app name, proxy headers (Liara sits behind a proxy) and rate limits.
func configureApp(app core.App) error {
	s := app.Settings()
	s.Meta.AppName = "Yadar"
	s.TrustedProxy.Headers = []string{"X-Forwarded-For", "X-Real-IP"}
	s.TrustedProxy.UseLeftmostIP = true
	s.RateLimits.Enabled = true
	want := []core.RateLimitRule{
		{Label: "/api/yadar/otp/", MaxRequests: 8, Duration: 60},
		{Label: "/api/yadar/ai/", MaxRequests: 30, Duration: 60},
		{Label: "/api/collections/users/auth-refresh", MaxRequests: 10, Duration: 60},
	}
	for _, rule := range want {
		found := false
		for _, existing := range s.RateLimits.Rules {
			if existing.Label == rule.Label {
				found = true
				break
			}
		}
		if !found {
			s.RateLimits.Rules = append(s.RateLimits.Rules, rule)
		}
	}
	return app.Save(s)
}

// ensureAdmin creates (or updates the password of) the admin panel account from ADMIN_EMAIL / ADMIN_PASSWORD.
func ensureAdmin(app core.App) error {
	email := strings.TrimSpace(os.Getenv("ADMIN_EMAIL"))
	password := os.Getenv("ADMIN_PASSWORD")
	if email == "" || password == "" {
		return nil
	}
	superusers, err := app.FindCollectionByNameOrId(core.CollectionNameSuperusers)
	if err != nil {
		return err
	}
	record, _ := app.FindAuthRecordByEmail(superusers, email)
	if record == nil {
		record = core.NewRecord(superusers)
		record.SetEmail(email)
	} else if record.ValidatePassword(password) {
		return nil
	}
	record.SetPassword(password)
	return app.Save(record)
}

// Settings is the single row of the "settings" collection.
type Settings struct {
	TokenUSD, Markup, SignupTokens, MinBalance float64
	DefaultTextModel, DefaultAudioModel        string
	FallbackInputPerM, FallbackOutputPerM      float64
	AIEnabled                                  bool
}

func loadSettings(app core.App) Settings {
	s := Settings{TokenUSD: 0.001, Markup: 1.5, SignupTokens: 100, MinBalance: 1,
		DefaultTextModel: "deepseek/deepseek-v4-flash", DefaultAudioModel: "google/gemini-2.5-flash",
		FallbackInputPerM: 1, FallbackOutputPerM: 4, AIEnabled: true}
	records, err := app.FindAllRecords("settings")
	if err != nil || len(records) == 0 {
		return s
	}
	r := records[0]
	if v := r.GetFloat("token_usd"); v > 0 {
		s.TokenUSD = v
	}
	if v := r.GetFloat("markup"); v > 0 {
		s.Markup = v
	}
	s.SignupTokens = r.GetFloat("signup_tokens")
	s.MinBalance = r.GetFloat("min_balance")
	if v := strings.TrimSpace(r.GetString("default_text_model")); v != "" {
		s.DefaultTextModel = v
	}
	if v := strings.TrimSpace(r.GetString("default_audio_model")); v != "" {
		s.DefaultAudioModel = v
	}
	if v := r.GetFloat("fallback_input_per_m"); v > 0 {
		s.FallbackInputPerM = v
	}
	if v := r.GetFloat("fallback_output_per_m"); v > 0 {
		s.FallbackOutputPerM = v
	}
	s.AIEnabled = r.GetBool("ai_enabled")
	return s
}
