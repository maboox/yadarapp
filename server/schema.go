package main

import (
	"github.com/pocketbase/pocketbase/core"
	"github.com/pocketbase/pocketbase/tools/types"
)

// Default prices are rough starting points (USD per 1M tokens / per audio minute).
// Check the provider's own price list and correct them in the admin panel.
var defaultModels = []struct {
	model, kind     string
	in, out, perMin float64
	note            string
}{
	{"deepseek/deepseek-v4-flash", "chat", 0.30, 1.20, 0, "تخمینی؛ قیمت را از سرویس‌دهنده چک کن"},
	{"google/gemini-2.5-flash", "chat", 0.30, 2.50, 0, "صدا را هم مستقیم می‌فهمد (input_audio)"},
	{"google/gemini-2.5-flash-lite", "chat", 0.10, 0.40, 0, "سریع و ارزان"},
	{"openai/gpt-4o-mini", "chat", 0.15, 0.60, 0, ""},
	{"openai/whisper-large-v3", "transcription", 0, 0, 0.006, "فقط تبدیل صدا به متن؛ تخمینی"},
}

// ensureSchema creates or upgrades every collection the server needs. It is idempotent and runs on each start,
// so new fields can be added in later versions without manual steps.
func ensureSchema(app core.App) error {
	plans, err := ensureCollection(app, "plans", func(c *core.Collection) {
		addField(c, &core.TextField{Name: "name", Required: true, Max: 60, Presentable: true})
		addField(c, &core.TextField{Name: "text_model", Max: 200, Help: "خالی = مدل پیش‌فرض تنظیمات"})
		addField(c, &core.TextField{Name: "audio_model", Max: 200, Help: "خالی = مدل پیش‌فرض تنظیمات"})
		addField(c, &core.TextField{Name: "note", Max: 500})
	})
	if err != nil {
		return err
	}

	users, err := app.FindCollectionByNameOrId("users")
	if err != nil {
		return err
	}
	addField(users, &core.TextField{Name: "phone", Max: 15, Presentable: true})
	addField(users, &core.NumberField{Name: "balance", Help: "موجودی توکن"})
	addField(users, &core.BoolField{Name: "unlimited", Help: "بدون محدودیت توکن"})
	addField(users, &core.RelationField{Name: "plan", CollectionId: plans.Id, MaxSelect: 1})
	addField(users, &core.TextField{Name: "text_model", Max: 200, Help: "مدل اختصاصی این کاربر؛ خالی = مدل پلن"})
	addField(users, &core.TextField{Name: "audio_model", Max: 200, Help: "مدل صدای اختصاصی؛ خالی = مدل پلن"})
	addField(users, &core.TextField{Name: "admin_note", Max: 500})
	if !hasIndex(users, "idx_users_phone") {
		users.AddIndex("idx_users_phone", true, "phone", "phone != ''")
	}
	// Users may only read their own record; balances and plans change only through this server or the admin.
	users.ListRule = nil
	users.ViewRule = types.Pointer("id = @request.auth.id")
	users.CreateRule = nil
	users.UpdateRule = nil
	users.DeleteRule = nil
	users.PasswordAuth.Enabled = false
	users.OTP.Enabled = false
	users.OAuth2.Enabled = false
	users.AuthToken.Duration = 60 * 60 * 24 * 90
	if err := app.Save(users); err != nil {
		return err
	}

	if _, err := ensureCollection(app, "providers", func(c *core.Collection) {
		addField(c, &core.TextField{Name: "name", Required: true, Max: 60, Presentable: true})
		addField(c, &core.TextField{Name: "base_url", Max: 300, Help: "مثلاً https://api.avalai.ir/v1 (بدون / آخر)"})
		addField(c, &core.TextField{Name: "api_key", Max: 500})
		addField(c, &core.NumberField{Name: "priority", Help: "عدد کمتر = اول امتحان می‌شود"})
		addField(c, &core.BoolField{Name: "enabled"})
		addField(c, &core.JSONField{Name: "model_map", MaxSize: 20000, Help: `نام مدل در این سرویس اگر فرق دارد، مثلاً {"deepseek/deepseek-v4-flash":"deepseek-v4-flash"}؛ مقدار "-" یعنی این سرویس آن مدل را ندارد`})
		addField(c, &core.JSONField{Name: "extra_body", MaxSize: 20000, Help: `فیلدهای اضافهٔ درخواست، مثلاً برای OpenRouter: {"reasoning":{"enabled":false}}`})
		addField(c, &core.BoolField{Name: "check_now", Help: "روشن کن و ذخیره کن تا وجود مدل‌ها در این سرویس بررسی شود"})
		addField(c, &core.TextField{Name: "check_result", Max: 5000})
	}); err != nil {
		return err
	}

	models, err := ensureCollection(app, "models", func(c *core.Collection) {
		addField(c, &core.TextField{Name: "model", Required: true, Max: 200, Presentable: true})
		addField(c, &core.SelectField{Name: "kind", Values: []string{"chat", "transcription"}, MaxSelect: 1,
			Help: "chat = مدل گفت‌وگو (صدا را هم اگر بفهمد مستقیم می‌گیرد)؛ transcription = فقط تبدیل صدا به متن"})
		addField(c, &core.NumberField{Name: "input_per_m", Help: "دلار به ازای ۱ میلیون توکن ورودی"})
		addField(c, &core.NumberField{Name: "output_per_m", Help: "دلار به ازای ۱ میلیون توکن خروجی"})
		addField(c, &core.NumberField{Name: "per_minute", Help: "دلار به ازای هر دقیقه صدا (برای transcription)"})
		addField(c, &core.TextField{Name: "note", Max: 500})
		if !hasIndex(c, "idx_models_model") {
			c.AddIndex("idx_models_model", true, "model", "")
		}
	})
	if err != nil {
		return err
	}
	for _, m := range defaultModels {
		if existing, _ := app.FindFirstRecordByData(models, "model", m.model); existing != nil {
			continue
		}
		r := core.NewRecord(models)
		r.Set("model", m.model)
		r.Set("kind", m.kind)
		r.Set("input_per_m", m.in)
		r.Set("output_per_m", m.out)
		r.Set("per_minute", m.perMin)
		r.Set("note", m.note)
		if err := app.Save(r); err != nil {
			return err
		}
	}

	settings, err := ensureCollection(app, "settings", func(c *core.Collection) {
		addField(c, &core.NumberField{Name: "token_usd", Help: "ارزش هر توکن به دلار (هزینهٔ واقعی ÷ این عدد = توکن)"})
		addField(c, &core.NumberField{Name: "markup", Help: "ضریب سود روی هزینهٔ واقعی، مثلاً 1.5"})
		addField(c, &core.NumberField{Name: "signup_tokens", Help: "توکن هدیهٔ ثبت‌نام"})
		addField(c, &core.NumberField{Name: "min_balance", Help: "حداقل موجودی برای شروع هر درخواست"})
		addField(c, &core.TextField{Name: "default_text_model", Max: 200})
		addField(c, &core.TextField{Name: "default_audio_model", Max: 200})
		addField(c, &core.NumberField{Name: "fallback_input_per_m", Help: "قیمت ورودی برای مدلی که در جدول models نیست"})
		addField(c, &core.NumberField{Name: "fallback_output_per_m", Help: "قیمت خروجی برای مدلی که در جدول models نیست"})
		addField(c, &core.BoolField{Name: "ai_enabled", Help: "خاموش = همهٔ درخواست‌های هوش مصنوعی موقتاً متوقف"})
	})
	if err != nil {
		return err
	}
	if n, _ := app.CountRecords(settings); n == 0 {
		r := core.NewRecord(settings)
		r.Set("token_usd", 0.001)
		r.Set("markup", 1.5)
		r.Set("signup_tokens", 15)
		r.Set("min_balance", 1)
		r.Set("default_text_model", "deepseek/deepseek-v4-flash")
		r.Set("default_audio_model", "google/gemini-2.5-flash")
		r.Set("fallback_input_per_m", 1.0)
		r.Set("fallback_output_per_m", 4.0)
		r.Set("ai_enabled", true)
		if err := app.Save(r); err != nil {
			return err
		}
	}

	if _, err := ensureCollection(app, "usage", func(c *core.Collection) {
		addField(c, &core.RelationField{Name: "user", CollectionId: users.Id, MaxSelect: 1, CascadeDelete: true})
		addField(c, &core.TextField{Name: "kind", Max: 20})
		addField(c, &core.TextField{Name: "model", Max: 200})
		addField(c, &core.TextField{Name: "provider", Max: 60})
		addField(c, &core.NumberField{Name: "prompt_tokens"})
		addField(c, &core.NumberField{Name: "completion_tokens"})
		addField(c, &core.NumberField{Name: "audio_seconds"})
		addField(c, &core.NumberField{Name: "cost_usd"})
		addField(c, &core.NumberField{Name: "tokens"})
		addField(c, &core.AutodateField{Name: "created", OnCreate: true})
		if !hasIndex(c, "idx_usage_user") {
			c.AddIndex("idx_usage_user", false, "user, created", "")
		}
	}); err != nil {
		return err
	}

	if _, err := ensureCollection(app, "otps", func(c *core.Collection) {
		addField(c, &core.TextField{Name: "phone", Max: 15})
		addField(c, &core.TextField{Name: "code_hash", Max: 100, Hidden: true})
		addField(c, &core.NumberField{Name: "expires"})
		addField(c, &core.NumberField{Name: "attempts"})
		addField(c, &core.TextField{Name: "ip", Max: 60})
		addField(c, &core.AutodateField{Name: "created", OnCreate: true})
		if !hasIndex(c, "idx_otps_phone") {
			c.AddIndex("idx_otps_phone", false, "phone, created", "")
		}
	}); err != nil {
		return err
	}
	return nil
}

// ensureCollection returns the named base collection, creating it if needed; configure adds missing fields.
// All rules stay nil, so only superusers (the admin panel) can read or change these collections directly.
func ensureCollection(app core.App, name string, configure func(*core.Collection)) (*core.Collection, error) {
	c, err := app.FindCollectionByNameOrId(name)
	if err != nil {
		c = core.NewBaseCollection(name)
	}
	configure(c)
	if err := app.Save(c); err != nil {
		return nil, err
	}
	return c, nil
}

func addField(c *core.Collection, f core.Field) {
	if c.Fields.GetByName(f.GetName()) == nil {
		c.Fields.Add(f)
	}
}

func hasIndex(c *core.Collection, name string) bool {
	return c.GetIndex(name) != ""
}
