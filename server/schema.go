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
	addField(users, &core.TextField{Name: "bazaar_id", Max: 100, Help: "شناسهٔ حساب کافه‌بازار"})
	addField(users, &core.JSONField{Name: "yadimo_owned", MaxSize: 20000, Help: "لوازم یادیموی خریده‌شده"})
	addField(users, &core.JSONField{Name: "yadimo_look", MaxSize: 5000, Help: "ظاهر انتخاب‌شدهٔ یادیمو"})
	addField(users, &core.JSONField{Name: "game", MaxSize: 20000, Help: "امتیاز، سطح، نشان‌ها و مأموریت‌ها (برنامه می‌فرستد)"})
	addField(users, &core.NumberField{Name: "level_override", Help: "سطح دستی؛ ۰ یعنی همان سطحی که برنامه حساب می‌کند"})
	addField(users, &core.BoolField{Name: "is_admin", Help: "مدیر: منوی مخفی برنامه و ارسال پیام همگانی"})
	addField(users, &core.TextField{Name: "username", Max: 20, Help: "نام کاربری یکتا (حروف کوچک انگلیسی)"})
	if !hasIndex(users, "idx_users_phone") {
		users.AddIndex("idx_users_phone", true, "phone", "phone != ''")
	}
	if !hasIndex(users, "idx_users_username") {
		users.AddIndex("idx_users_username", true, "username", "username != ''")
	}
	if !hasIndex(users, "idx_users_bazaar") {
		users.AddIndex("idx_users_bazaar", true, "bazaar_id", "bazaar_id != ''")
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

	// New price fields get their starting price once; after that the admin panel decides (0 = free).
	var newPrices []string
	if existing, _ := app.FindCollectionByNameOrId("settings"); existing != nil {
		for tier := range yadimoDefaultPrices {
			if existing.Fields.GetByName("yadimo_price_"+tier) == nil {
				newPrices = append(newPrices, tier)
			}
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
		addField(c, &core.TextField{Name: "public_url", Max: 200, Help: "آدرس عمومی سرور، مثلاً https://yadar.pish.run (برای ورود با بازار)"})
		addField(c, &core.TextField{Name: "bazaar_client_id", Max: 200, Help: "پیشخان بازار ← ورود با بازار ← ClientID"})
		addField(c, &core.TextField{Name: "bazaar_client_secret", Max: 500, Help: "پیشخان بازار ← ورود با بازار ← Secret"})
		addField(c, &core.TextField{Name: "bazaar_api_token", Max: 500, Help: "پیشخان بازار ← API پیشخان ← دریافت توکن (برای بررسی خریدها)"})
		addField(c, &core.TextField{Name: "bazaar_rsa_key", Max: 2000, Help: "پیشخان بازار ← پرداخت درون‌برنامه‌ای ← کلید RSA (بررسی امضای خریدها بدون تماس با بازار)"})
		addField(c, &core.BoolField{Name: "app_login_disabled", Help: "روشن = ورود درون‌برنامه‌ای با بازار بسته شود (مثلاً اگر سوءاستفاده دیدی)"})
		addField(c, &core.NumberField{Name: "yadimo_price_basic", Help: "قیمت لوازم سادهٔ یادیمو (توکن)"})
		addField(c, &core.NumberField{Name: "yadimo_price_special", Help: "قیمت لوازم ویژهٔ یادیمو (توکن)"})
		addField(c, &core.NumberField{Name: "yadimo_price_costume", Help: "قیمت لباس شخصیت‌ها (توکن)"})
	})
	if err != nil {
		return err
	}
	if len(newPrices) > 0 {
		if records, _ := app.FindAllRecords(settings); len(records) > 0 {
			for _, tier := range newPrices {
				records[0].Set("yadimo_price_"+tier, yadimoDefaultPrices[tier])
			}
			if err := app.Save(records[0]); err != nil {
				return err
			}
		}
	}
	if n, _ := app.CountRecords(settings); n == 0 {
		r := core.NewRecord(settings)
		for tier, price := range yadimoDefaultPrices {
			r.Set("yadimo_price_"+tier, price)
		}
		r.Set("token_usd", 0.0025) // about one typical task
		r.Set("markup", 1.0)
		r.Set("signup_tokens", 10)
		r.Set("min_balance", 0.5)
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

	if _, err := ensureCollection(app, "logins", func(c *core.Collection) {
		addField(c, &core.TextField{Name: "state", Max: 100})
		addField(c, &core.TextField{Name: "challenge", Max: 100})
		addField(c, &core.TextField{Name: "app", Max: 100})
		addField(c, &core.TextField{Name: "account_id", Max: 100})
		addField(c, &core.NumberField{Name: "expires"})
		addField(c, &core.TextField{Name: "ip", Max: 60})
		addField(c, &core.AutodateField{Name: "created", OnCreate: true})
		if !hasIndex(c, "idx_logins_state") {
			c.AddIndex("idx_logins_state", true, "state", "")
		}
		if !hasIndex(c, "idx_logins_challenge") {
			c.AddIndex("idx_logins_challenge", true, "challenge", "")
		}
	}); err != nil {
		return err
	}

	packs, err := ensureCollection(app, "packs", func(c *core.Collection) {
		addField(c, &core.TextField{Name: "sku", Required: true, Max: 100, Presentable: true, Help: "شناسهٔ محصول در پیشخان بازار"})
		addField(c, &core.NumberField{Name: "tokens", Help: "توکنی که با هر خرید اضافه می‌شود"})
		addField(c, &core.TextField{Name: "title", Max: 100})
		addField(c, &core.TextField{Name: "price_label", Max: 100, Help: "فقط برای نمایش؛ قیمت واقعی را بازار تعیین می‌کند"})
		addField(c, &core.TextField{Name: "note", Max: 200})
		addField(c, &core.NumberField{Name: "sort"})
		addField(c, &core.BoolField{Name: "enabled"})
		if !hasIndex(c, "idx_packs_sku") {
			c.AddIndex("idx_packs_sku", true, "sku", "")
		}
	})
	if err != nil {
		return err
	}
	if n, _ := app.CountRecords(packs); n == 0 {
		for i, p := range defaultPacks {
			r := core.NewRecord(packs)
			r.Set("sku", p.sku)
			r.Set("tokens", p.tokens)
			r.Set("title", p.title)
			r.Set("price_label", p.price)
			r.Set("note", p.note)
			r.Set("sort", i+1)
			r.Set("enabled", true)
			if err := app.Save(r); err != nil {
				return err
			}
		}
	}

	if _, err := ensureCollection(app, "purchases", func(c *core.Collection) {
		addField(c, &core.RelationField{Name: "user", CollectionId: users.Id, MaxSelect: 1})
		addField(c, &core.TextField{Name: "sku", Max: 100})
		addField(c, &core.TextField{Name: "token", Max: 500})
		addField(c, &core.TextField{Name: "order_id", Max: 200})
		addField(c, &core.TextField{Name: "package", Max: 100})
		addField(c, &core.NumberField{Name: "tokens"})
		addField(c, &core.BoolField{Name: "consumed", Help: "در بازار مصرف شد (کاربر می‌تواند دوباره همین بسته را بخرد)"})
		addField(c, &core.AutodateField{Name: "created", OnCreate: true})
		if !hasIndex(c, "idx_purchases_token") {
			c.AddIndex("idx_purchases_token", true, "token", "")
		}
	}); err != nil {
		return err
	}

	// One small row per signed-in user, refreshed at most once a day by the app: just totals, never content.
	if _, err := ensureCollection(app, "user_stats", func(c *core.Collection) {
		addField(c, &core.RelationField{Name: "user", CollectionId: users.Id, MaxSelect: 1, CascadeDelete: true})
		addField(c, &core.NumberField{Name: "reminders", Help: "یادآوری‌های فعلی"})
		addField(c, &core.NumberField{Name: "notes"})
		addField(c, &core.NumberField{Name: "done", Help: "انجام‌شده‌ها در کل"})
		addField(c, &core.NumberField{Name: "missed"})
		addField(c, &core.NumberField{Name: "ai_requests", Help: "درخواست‌های دستیار در ۳۰ روز اخیر"})
		addField(c, &core.NumberField{Name: "days_used", Help: "تعداد روزهایی که برنامه باز شده"})
		addField(c, &core.NumberField{Name: "stars", Help: "امتیازی که کاربر داده؛ ۰ یعنی هنوز نداده"})
		addField(c, &core.TextField{Name: "version", Max: 20})
		addField(c, &core.NumberField{Name: "version_code"})
		addField(c, &core.TextField{Name: "language", Max: 10})
		addField(c, &core.TextField{Name: "theme", Max: 20})
		addField(c, &core.AutodateField{Name: "created", OnCreate: true})
		addField(c, &core.AutodateField{Name: "updated", OnCreate: true, OnUpdate: true})
		if !hasIndex(c, "idx_user_stats_user") {
			c.AddIndex("idx_user_stats_user", true, "user", "")
		}
	}); err != nil {
		return err
	}

	// A ready-made summary for the admin panel (Collections → stats_summary): how much people use the app.
	if existing, _ := app.FindCollectionByNameOrId("stats_summary"); existing == nil {
		view := core.NewViewCollection("stats_summary")
		view.ViewQuery = `SELECT 'all' AS id,
			COUNT(*) AS users,
			SUM(CASE WHEN updated >= date('now','-7 day') THEN 1 ELSE 0 END) AS active_7d,
			ROUND(AVG(reminders),1) AS avg_reminders,
			MAX(reminders) AS max_reminders,
			ROUND(AVG(notes),1) AS avg_notes,
			ROUND(AVG(done),1) AS avg_done,
			ROUND(AVG(ai_requests),1) AS avg_ai_30d,
			ROUND(AVG(days_used),1) AS avg_days_used,
			SUM(CASE WHEN stars > 0 THEN 1 ELSE 0 END) AS rated,
			ROUND(AVG(CASE WHEN stars > 0 THEN stars END),2) AS avg_stars
			FROM user_stats`
		if err := app.Save(view); err != nil {
			return err
		}
	}

	// Messages to every user (see announce.go).
	if _, err := ensureCollection(app, "announcements", func(c *core.Collection) {
		addField(c, &core.TextField{Name: "title", Max: 80, Presentable: true})
		addField(c, &core.TextField{Name: "body", Max: 1000})
		addField(c, &core.SelectField{Name: "style", Values: []string{"popup", "notification", "fullscreen"}, MaxSelect: 1,
			Help: "popup = پنجره داخل برنامه؛ notification = اعلان؛ fullscreen = صفحهٔ کامل با یادیمو"})
		addField(c, &core.TextField{Name: "button_text", Max: 30})
		addField(c, &core.TextField{Name: "button_url", Max: 300, Help: "مثلاً bazaar://details?id=com.yadavard.app.plus برای صفحهٔ برنامه در بازار"})
		addField(c, &core.NumberField{Name: "below_version", Help: "فقط برای نسخه‌های قدیمی‌تر از این کد نسخه؛ ۰ = همه"})
		addField(c, &core.JSONField{Name: "target", MaxSize: 20000, Help: `مخاطب: {"logged":"in|out","min_version":0,"max_version":0,"usage":"active|light|inactive","users":["id"]}؛ خالی = همه`})
		addField(c, &core.DateField{Name: "ends", Help: "بعد از این تاریخ نشان داده نمی‌شود؛ خالی = همیشه"})
		addField(c, &core.BoolField{Name: "active"})
		addField(c, &core.AutodateField{Name: "created", OnCreate: true})
		if !hasIndex(c, "idx_announcements_created") {
			c.AddIndex("idx_announcements_created", false, "created", "")
		}
	}); err != nil {
		return err
	}

	// Token rewards of the starter missions: one row per paid mission, so each pays once per account and phone.
	if _, err := ensureCollection(app, "rewards", func(c *core.Collection) {
		addField(c, &core.RelationField{Name: "user", CollectionId: users.Id, MaxSelect: 1, CascadeDelete: false})
		addField(c, &core.TextField{Name: "mission", Max: 40, Presentable: true})
		addField(c, &core.TextField{Name: "device", Max: 100})
		addField(c, &core.NumberField{Name: "tokens"})
		addField(c, &core.AutodateField{Name: "created", OnCreate: true})
		if !hasIndex(c, "idx_rewards_user") {
			c.AddIndex("idx_rewards_user", true, "user, mission", "")
		}
		if !hasIndex(c, "idx_rewards_device") {
			c.AddIndex("idx_rewards_device", true, "device, mission", "")
		}
	}); err != nil {
		return err
	}

	if _, err := ensureCollection(app, "gifts", func(c *core.Collection) {
		addField(c, &core.TextField{Name: "device", Max: 100})
		addField(c, &core.TextField{Name: "ip", Max: 60})
		addField(c, &core.RelationField{Name: "user", CollectionId: users.Id, MaxSelect: 1, CascadeDelete: false})
		addField(c, &core.AutodateField{Name: "created", OnCreate: true})
		if !hasIndex(c, "idx_gifts_device") {
			c.AddIndex("idx_gifts_device", false, "device", "")
		}
		if !hasIndex(c, "idx_gifts_ip") {
			c.AddIndex("idx_gifts_ip", false, "ip, created", "")
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

var defaultPacks = []struct {
	sku, title, price, note string
	tokens                  float64
}{
	{"tokens_50", "۵۰ توکن", "۸۹ هزار تومان", "", 50},
	{"tokens_150", "۱۵۰ توکن", "۲۲۹ هزار تومان", "", 150},
	{"tokens_400", "۴۰۰ توکن", "۵۴۹ هزار تومان", "پرفروش", 400},
	{"tokens_800", "۸۰۰ توکن", "۹۹۰ هزار تومان", "به‌صرفه‌ترین", 800},
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
