package main

import (
	"bytes"
	"context"
	"crypto"
	"crypto/rsa"
	"crypto/sha1"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"html"
	"io"
	"log"
	"math"
	"net/http"
	"net/url"
	"os"
	"regexp"
	"strings"
	"time"

	"github.com/pocketbase/dbx"
	"github.com/pocketbase/pocketbase/apis"
	"github.com/pocketbase/pocketbase/core"
	"github.com/pocketbase/pocketbase/tools/security"
)

// Cafe Bazaar endpoints; variables so tests can point them at a fake server.
var (
	bazaarOAuthURL    = "https://cafebazaar.ir/user/oauth"
	bazaarTokenURL    = "https://account.cafebazaar.ir/api/v0/tokens"
	bazaarUserInfoURL = "https://account.cafebazaar.ir/api/v0/userinfo"
	bazaarAPIBase     = "https://pardakht.cafebazaar.ir/devapi/v2/api"
)

func init() {
	// Local testing only: send every Bazaar call to a fake server.
	if base := strings.TrimRight(os.Getenv("YADAR_FAKE_BAZAAR"), "/"); base != "" {
		bazaarOAuthURL, bazaarTokenURL = base+"/user/oauth", base+"/api/v0/tokens"
		bazaarUserInfoURL, bazaarAPIBase = base+"/api/v0/userinfo", base+"/devapi/v2/api"
	}
}

// bazaarClient talks to Cafe Bazaar. Bazaar refuses many non-Iranian addresses, so a server abroad can send these
// calls through a proxy inside Iran with BAZAAR_PROXY (http://user:pass@host:port or socks5://host:port).
var bazaarClient = func() *http.Client {
	transport := http.DefaultTransport.(*http.Transport).Clone()
	if raw := strings.TrimSpace(os.Getenv("BAZAAR_PROXY")); raw != "" {
		if u, err := url.Parse(raw); err == nil {
			transport.Proxy = http.ProxyURL(u)
		} else {
			log.Println("BAZAAR_PROXY is not a valid address:", err)
		}
	}
	return &http.Client{Timeout: 30 * time.Second, Transport: transport}
}()

func bazaarDo(req *http.Request) ([]byte, error) {
	req.Header.Set("User-Agent", "Mozilla/5.0 (Linux; Android 14) YadarServer/1.0")
	req.Header.Set("Accept", "application/json")
	resp, err := bazaarClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	data, _ := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		msg := string(data)
		if strings.Contains(msg, "nginx") && resp.StatusCode == http.StatusForbidden {
			msg = "Bazaar refused this server's address (403). The server is probably outside Iran; set BAZAAR_PROXY."
		} else if len(msg) > 300 {
			msg = msg[:300]
		}
		return nil, &callError{resp.StatusCode, msg}
	}
	return data, nil
}

const loginLifetime = 15 * time.Minute

var hex64 = regexp.MustCompile(`^[0-9a-f]{64}$`)

// bazaarConfig comes from the settings row (editable in the admin panel); environment variables win when set.
type bazaarConfig struct {
	ClientID, ClientSecret, APIToken, PublicURL, RSAKey string
	Packages                                            []string
}

// canVerify is true when purchases can be checked, by Bazaar's API or by signature.
func (c bazaarConfig) canVerify() bool { return c.APIToken != "" || c.RSAKey != "" }

func loadBazaar(app core.App) bazaarConfig {
	var c bazaarConfig
	if records, err := app.FindAllRecords("settings"); err == nil && len(records) > 0 {
		r := records[0]
		c.ClientID = strings.TrimSpace(r.GetString("bazaar_client_id"))
		c.ClientSecret = strings.TrimSpace(r.GetString("bazaar_client_secret"))
		c.APIToken = strings.TrimSpace(r.GetString("bazaar_api_token"))
		c.PublicURL = strings.TrimSpace(r.GetString("public_url"))
		c.RSAKey = strings.TrimSpace(r.GetString("bazaar_rsa_key"))
	}
	for env, field := range map[string]*string{"BAZAAR_CLIENT_ID": &c.ClientID, "BAZAAR_CLIENT_SECRET": &c.ClientSecret,
		"BAZAAR_API_TOKEN": &c.APIToken, "PUBLIC_URL": &c.PublicURL, "BAZAAR_RSA_KEY": &c.RSAKey} {
		if v := strings.TrimSpace(os.Getenv(env)); v != "" {
			*field = v
		}
	}
	c.PublicURL = strings.TrimRight(c.PublicURL, "/")
	packages := os.Getenv("BAZAAR_PACKAGES")
	if strings.TrimSpace(packages) == "" {
		packages = "com.yadavard.app,com.yadavard.app.plus"
	}
	for _, p := range strings.Split(packages, ",") {
		if p = strings.TrimSpace(p); p != "" {
			c.Packages = append(c.Packages, p)
		}
	}
	return c
}

func (c bazaarConfig) allows(pkg string) bool {
	for _, p := range c.Packages {
		if p == pkg {
			return true
		}
	}
	return false
}

// redirectURL is the callback registered in the Bazaar panel ("ورود با بازار" → Redirect URL).
func (c bazaarConfig) redirectURL(e *core.RequestEvent) string {
	base := c.PublicURL
	if base == "" {
		base = "https://" + e.Request.Host
	}
	return base + "/api/yadar/bazaar/callback"
}

// ---- login ----
//
// The app makes a random verifier, opens /bazaar/start with its SHA-256 in the browser, the user signs in on
// Bazaar's site, Bazaar sends them back to /bazaar/callback where this server learns their account id, and the
// app then claims the login with the verifier. Only the app that started the login knows the verifier, so a
// leaked state or callback link cannot be used to sign in.

func handleBazaarStart(e *core.RequestEvent) error {
	q := e.Request.URL.Query()
	challenge := strings.ToLower(strings.TrimSpace(q.Get("challenge")))
	if !hex64.MatchString(challenge) {
		return page(e, http.StatusBadRequest, "درخواست نامعتبر است", "از داخل برنامهٔ یادار دوباره «ورود با بازار» را بزن.", "")
	}
	cfg := loadBazaar(e.App)
	if cfg.ClientID == "" || cfg.ClientSecret == "" {
		return page(e, http.StatusServiceUnavailable, "ورود با بازار هنوز راه‌اندازی نشده", "کمی بعد دوباره امتحان کن.", "")
	}
	pkg := q.Get("app")
	if !cfg.allows(pkg) {
		pkg = ""
	}
	app := e.App
	// Old, unfinished logins are of no use to anyone.
	if old, err := app.FindRecordsByFilter("logins", "expires < {:t}", "", 200, 0,
		dbx.Params{"t": time.Now().Add(-time.Hour).UnixMilli()}); err == nil {
		for _, r := range old {
			_ = app.Delete(r)
		}
	}
	if existing, _ := app.FindFirstRecordByData("logins", "challenge", challenge); existing != nil {
		_ = app.Delete(existing)
	}
	col, err := app.FindCollectionByNameOrId("logins")
	if err != nil {
		return err
	}
	state := security.RandomString(32)
	r := core.NewRecord(col)
	r.Set("state", state)
	r.Set("challenge", challenge)
	r.Set("app", pkg)
	r.Set("expires", time.Now().Add(loginLifetime).UnixMilli())
	r.Set("ip", e.RealIP())
	if err := app.Save(r); err != nil {
		return err
	}
	target := bazaarOAuthURL + "?redirect_url=" + url.QueryEscape(cfg.redirectURL(e)) +
		"&client_id=" + url.QueryEscape(cfg.ClientID) + "&state=" + url.QueryEscape(state)
	return e.Redirect(http.StatusFound, target)
}

func handleBazaarCallback(e *core.RequestEvent) error {
	q := e.Request.URL.Query()
	app := e.App
	var login *core.Record
	if state := q.Get("state"); state != "" {
		login, _ = app.FindFirstRecordByData("logins", "state", state)
	}
	if login == nil || time.Now().UnixMilli() > int64(login.GetFloat("expires")) {
		return page(e, http.StatusBadRequest, "این ورود منقضی شده", "به برنامهٔ یادار برگرد و دوباره «ورود با بازار» را بزن.", "")
	}
	scheme := login.GetString("app")
	code := q.Get("code")
	if q.Get("error") != "" || code == "" {
		return page(e, http.StatusOK, "ورود انجام نشد", "اجازهٔ ورود داده نشد. به یادار برگرد و اگر خواستی دوباره امتحان کن.", scheme)
	}
	// Bazaar's own example unescapes the code once more.
	if strings.Contains(code, "%") {
		if u, err := url.QueryUnescape(code); err == nil {
			code = u
		}
	}
	cfg := loadBazaar(app)
	ctx, cancel := context.WithTimeout(e.Request.Context(), 30*time.Second)
	defer cancel()
	accountID, err := bazaarAccountID(ctx, cfg, code, cfg.redirectURL(e))
	if err != nil {
		log.Println("bazaar login:", err)
		// The reason is Bazaar's own answer (never our secret), shown small so a screenshot is enough to diagnose.
		return page(e, http.StatusBadGateway, "ارتباط با بازار برقرار نشد",
			"به یادار برگرد و دوباره امتحان کن.\n\nجزئیات: "+err.Error(), scheme)
	}
	login.Set("account_id", accountID)
	if err := app.Save(login); err != nil {
		return err
	}
	return page(e, http.StatusOK, "ورود انجام شد ✓", "حالا به برنامهٔ یادار برگرد.", scheme)
}

// bazaarAccountID trades the one-time code for an access token and reads the user's stable Bazaar account id.
func bazaarAccountID(ctx context.Context, cfg bazaarConfig, code, redirect string) (string, error) {
	// Standard OAuth2 servers want the same redirect address back; Bazaar's docs call it redirect_url.
	form := url.Values{"grant_type": {"authorization_code"}, "code": {code},
		"client_id": {cfg.ClientID}, "client_secret": {cfg.ClientSecret},
		"redirect_uri": {redirect}, "redirect_url": {redirect}}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, bazaarTokenURL, strings.NewReader(form.Encode()))
	if err != nil {
		return "", err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	data, err := bazaarDo(req)
	if err != nil {
		return "", fmt.Errorf("token: %w", err)
	}
	var tok struct {
		AccessToken string `json:"access_token"`
		TokenType   string `json:"token_type"`
	}
	if err := json.Unmarshal(data, &tok); err != nil || tok.AccessToken == "" {
		return "", fmt.Errorf("token: unexpected answer %.200s", data)
	}
	if tok.TokenType == "" {
		tok.TokenType = "Bearer"
	}
	req, err = http.NewRequestWithContext(ctx, http.MethodGet, bazaarUserInfoURL, nil)
	if err != nil {
		return "", err
	}
	req.Header.Set("Authorization", tok.TokenType+" "+tok.AccessToken)
	data, err = bazaarDo(req)
	if err != nil {
		return "", fmt.Errorf("userinfo: %w", err)
	}
	var info map[string]any
	if err := json.Unmarshal(data, &info); err != nil {
		return "", fmt.Errorf("userinfo: unexpected answer %.200s", data)
	}
	id := ""
	switch v := info["account_id"].(type) {
	case string:
		id = strings.TrimSpace(v)
	case float64:
		id = fmt.Sprintf("%.0f", v)
	}
	if id == "" {
		return "", fmt.Errorf("userinfo: no account_id in %.200s", data)
	}
	return id, nil
}

func handleBazaarClaim(e *core.RequestEvent) error {
	var body struct {
		Verifier string `json:"verifier"`
	}
	if err := e.BindBody(&body); err != nil || len(body.Verifier) < 32 {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	sum := sha256.Sum256([]byte(body.Verifier))
	app := e.App
	login, _ := app.FindFirstRecordByData("logins", "challenge", hex.EncodeToString(sum[:]))
	if login == nil {
		return apis.NewNotFoundError("ورود پیدا نشد؛ دوباره «ورود با بازار» را بزن", nil)
	}
	if time.Now().UnixMilli() > int64(login.GetFloat("expires")) {
		_ = app.Delete(login)
		return apis.NewBadRequestError("ورود منقضی شد؛ دوباره امتحان کن", nil)
	}
	accountID := login.GetString("account_id")
	if accountID == "" {
		return e.JSON(http.StatusOK, map[string]any{"pending": true})
	}
	_ = app.Delete(login)
	user, created, err := findOrCreateBazaarUser(app, accountID)
	if err != nil {
		return err
	}
	token, err := user.NewAuthToken()
	if err != nil {
		return err
	}
	return e.JSON(http.StatusOK, map[string]any{"token": token, "new": created, "profile": profile(app, user)})
}

var accountIDPattern = regexp.MustCompile(`^[A-Za-z0-9_.:-]{6,100}$`)

// handleBazaarAppLogin signs in with the account id the Bazaar app hands to Yadar (in-app login). The server
// cannot check that id with Bazaar, so the free signup tokens are limited to one per device and a few per
// network address a day; tokens bought on Bazaar are always checked separately.
func handleBazaarAppLogin(e *core.RequestEvent) error {
	var body struct {
		AccountID string `json:"account_id"`
		Device    string `json:"device"`
	}
	if err := e.BindBody(&body); err != nil || !accountIDPattern.MatchString(body.AccountID) {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	if loadSettings(e.App).AppLoginDisabled {
		return apis.NewForbiddenError("ورود با بازار موقتاً بسته است", nil)
	}
	app := e.App
	user, _ := app.FindFirstRecordByData("users", "bazaar_id", body.AccountID)
	created := false
	if user == nil {
		gift := 0.0
		if giftAllowed(app, body.Device, e.RealIP()) {
			gift = loadSettings(app).SignupTokens
		}
		users, err := app.FindCollectionByNameOrId("users")
		if err != nil {
			return err
		}
		sum := sha256.Sum256([]byte(body.AccountID))
		user = core.NewRecord(users)
		user.Set("bazaar_id", body.AccountID)
		user.SetEmail("bz" + hex.EncodeToString(sum[:12]) + "@bazaar.yadar.app")
		user.SetPassword(security.RandomString(40))
		user.SetVerified(true)
		user.Set("balance", gift)
		if err := app.Save(user); err != nil {
			return err
		}
		created = true
		if gift > 0 {
			recordGift(app, body.Device, e.RealIP(), user.Id)
		}
	}
	token, err := user.NewAuthToken()
	if err != nil {
		return err
	}
	return e.JSON(http.StatusOK, map[string]any{"token": token, "new": created, "profile": profile(app, user)})
}

func deviceHash(device string) string {
	device = strings.TrimSpace(device)
	if device == "" {
		return ""
	}
	sum := sha256.Sum256([]byte("yadar-device|" + device))
	return hex.EncodeToString(sum[:16])
}

// giftAllowed gives the signup tokens once per device and at most a few times a day per network address.
func giftAllowed(app core.App, device, ip string) bool {
	d := deviceHash(device)
	if d == "" {
		return false
	}
	if r, _ := app.FindFirstRecordByData("gifts", "device", d); r != nil {
		return false
	}
	since := time.Now().Add(-24 * time.Hour).UTC().Format("2006-01-02 15:04:05.000Z")
	n, _ := app.CountRecords("gifts", dbx.NewExp("ip = {:ip} AND created > {:t}", dbx.Params{"ip": ip, "t": since}))
	return n < 5
}

func recordGift(app core.App, device, ip, userID string) {
	col, err := app.FindCollectionByNameOrId("gifts")
	if err != nil {
		return
	}
	r := core.NewRecord(col)
	r.Set("device", deviceHash(device))
	r.Set("ip", ip)
	r.Set("user", userID)
	if err := app.Save(r); err != nil {
		log.Println("gift:", err)
	}
}

func findOrCreateBazaarUser(app core.App, accountID string) (*core.Record, bool, error) {
	if u, _ := app.FindFirstRecordByData("users", "bazaar_id", accountID); u != nil {
		return u, false, nil
	}
	users, err := app.FindCollectionByNameOrId("users")
	if err != nil {
		return nil, false, err
	}
	sum := sha256.Sum256([]byte(accountID))
	u := core.NewRecord(users)
	u.Set("bazaar_id", accountID)
	u.SetEmail("bz" + hex.EncodeToString(sum[:12]) + "@bazaar.yadar.app")
	u.SetPassword(security.RandomString(40))
	u.SetVerified(true)
	u.Set("balance", loadSettings(app).SignupTokens)
	if err := app.Save(u); err != nil {
		return nil, false, err
	}
	return u, true, nil
}

// page is the small screen the browser shows after Bazaar sends the user back; it also tries to reopen the app.
func page(e *core.RequestEvent, status int, title, text, scheme string) error {
	back := ""
	if scheme != "" {
		link := html.EscapeString(scheme + "://bazaar-login")
		back = `<a class="btn" href="` + link + `">بازگشت به یادار</a><script>setTimeout(function(){location.href="` + link + `"},600)</script>`
	}
	doc := `<!doctype html><html lang="fa" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>یادار</title><style>body{font-family:Vazirmatn,Tahoma,sans-serif;background:#f6f4ff;color:#1d1b20;display:flex;min-height:100vh;margin:0;align-items:center;justify-content:center}
.card{background:#fff;border-radius:24px;padding:32px 24px;max-width:360px;margin:16px;text-align:center;box-shadow:0 4px 24px #0001}
h1{font-size:22px;margin:0 0 12px}p{line-height:1.8;color:#49454f;white-space:pre-line;overflow-wrap:anywhere}.btn{display:inline-block;margin-top:16px;background:#6750a4;color:#fff;
padding:12px 28px;border-radius:999px;text-decoration:none}</style></head><body><div class="card"><h1>` + html.EscapeString(title) +
		`</h1><p>` + html.EscapeString(text) + `</p>` + back + `</div></body></html>`
	return e.HTML(status, doc)
}

// ---- token packs ----

func handlePacks(e *core.RequestEvent) error {
	records, err := e.App.FindRecordsByFilter("packs", "enabled = true", "sort,tokens", 50, 0)
	if err != nil {
		return err
	}
	items := make([]map[string]any, 0, len(records))
	for _, r := range records {
		items = append(items, map[string]any{"sku": r.GetString("sku"), "tokens": r.GetFloat("tokens"),
			"title": r.GetString("title"), "price": r.GetString("price_label"), "note": r.GetString("note")})
	}
	return e.JSON(http.StatusOK, map[string]any{"items": items, "ready": loadBazaar(e.App).canVerify()})
}

type bazaarPurchase struct {
	PurchaseState    int    `json:"purchaseState"`
	ConsumptionState int    `json:"consumptionState"`
	DeveloperPayload string `json:"developerPayload"`
}

// validatePurchase asks Bazaar whether this purchase really happened (and was not refunded).
func validatePurchase(ctx context.Context, cfg bazaarConfig, pkg, sku, token string) (*bazaarPurchase, error) {
	u := fmt.Sprintf("%s/validate/%s/inapp/%s/purchases/%s/", bazaarAPIBase,
		url.PathEscape(pkg), url.PathEscape(sku), url.PathEscape(token))
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, u, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("CAFEBAZAAR-PISHKHAN-API-SECRET", cfg.APIToken)
	data, err := bazaarDo(req)
	if err != nil {
		return nil, err
	}
	var p bazaarPurchase
	if err := json.Unmarshal(data, &p); err != nil {
		return nil, fmt.Errorf("validate: unexpected answer %.200s", data)
	}
	return &p, nil
}

// consumePurchase marks a pack as used on Bazaar so the user can buy it again. Already consumed counts as done.
func consumePurchase(ctx context.Context, cfg bazaarConfig, pkg, token string) error {
	body, _ := json.Marshal(map[string]string{"token": token})
	req, err := http.NewRequestWithContext(ctx, http.MethodPost,
		fmt.Sprintf("%s/consume/%s/purchases/", bazaarAPIBase, url.PathEscape(pkg)), bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("CAFEBAZAAR-PISHKHAN-API-SECRET", cfg.APIToken)
	req.Header.Set("Content-Type", "application/json")
	_, err = bazaarDo(req)
	if ce, ok := err.(*callError); ok && strings.Contains(ce.Msg, "already_consumed") {
		return nil
	}
	return err
}

// signedPurchase is the purchase data Bazaar signs with the app's key (Poolakey's originalJson).
type signedPurchase struct {
	OrderID          string `json:"orderId"`
	PurchaseToken    string `json:"purchaseToken"`
	DeveloperPayload string `json:"developerPayload"`
	PackageName      string `json:"packageName"`
	ProductID        string `json:"productId"`
	PurchaseState    int    `json:"purchaseState"`
}

// verifySignature checks Bazaar's SHA1withRSA signature of the purchase data with the RSA key from the panel
// («پرداخت درون‌برنامه‌ای» ← کلید RSA). Nobody without Bazaar's private key can produce a valid one, so this
// proves the purchase without calling Bazaar.
func verifySignature(publicKey, data, signature string) error {
	der, err := base64.StdEncoding.DecodeString(strings.Join(strings.Fields(publicKey), ""))
	if err != nil {
		return fmt.Errorf("RSA key is not base64: %w", err)
	}
	parsed, err := x509.ParsePKIXPublicKey(der)
	if err != nil {
		return fmt.Errorf("RSA key: %w", err)
	}
	key, ok := parsed.(*rsa.PublicKey)
	if !ok {
		return fmt.Errorf("RSA key is not an RSA public key")
	}
	sig, err := base64.StdEncoding.DecodeString(strings.TrimSpace(signature))
	if err != nil {
		return fmt.Errorf("signature is not base64")
	}
	sum := sha1.Sum([]byte(data))
	return rsa.VerifyPKCS1v15(key, crypto.SHA1, sum[:], sig)
}

func handlePurchase(e *core.RequestEvent) error {
	var body struct {
		Package      string `json:"package"`
		SKU          string `json:"sku"`
		Token        string `json:"token"`
		OrderID      string `json:"order_id"`
		OriginalJSON string `json:"original_json"`
		Signature    string `json:"signature"`
	}
	if err := e.BindBody(&body); err != nil || body.SKU == "" || body.Token == "" || len(body.Token) > 500 ||
		len(body.OriginalJSON) > 5000 || len(body.Signature) > 2000 {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	app := e.App
	cfg := loadBazaar(app)
	bySignature := cfg.RSAKey != "" && body.Signature != "" && body.OriginalJSON != ""
	if !bySignature && cfg.APIToken == "" {
		return apis.NewApiError(http.StatusServiceUnavailable, "خرید هنوز راه‌اندازی نشده", nil)
	}
	if !cfg.allows(body.Package) {
		return apis.NewBadRequestError("این نسخهٔ برنامه برای خرید شناخته نشد", nil)
	}
	pack, _ := app.FindFirstRecordByData("packs", "sku", body.SKU)
	if pack == nil {
		return apis.NewBadRequestError("این بسته شناخته نشد", nil)
	}
	ctx, cancel := context.WithTimeout(e.Request.Context(), 30*time.Second)
	defer cancel()
	answer := func(added float64, already bool, r *core.Record) error {
		user, _ := app.FindRecordById("users", e.Auth.Id)
		// "consume" asks the app to consume the purchase on Bazaar itself when the server could not.
		return e.JSON(http.StatusOK, map[string]any{"ok": true, "added": added, "already": already,
			"consume": r != nil && !r.GetBool("consumed"), "profile": profile(app, user)})
	}

	if existing, _ := app.FindFirstRecordByData("purchases", "token", body.Token); existing != nil {
		if existing.GetString("user") != e.Auth.Id {
			return apis.NewForbiddenError("این خرید مال حساب دیگری است", nil)
		}
		finishConsume(app, ctx, cfg, existing)
		return answer(0, true, existing)
	}

	if bySignature {
		if err := verifySignature(cfg.RSAKey, body.OriginalJSON, body.Signature); err != nil {
			log.Println("purchase signature:", err)
			return apis.NewForbiddenError("امضای این خرید معتبر نیست", nil)
		}
		var p signedPurchase
		if err := json.Unmarshal([]byte(body.OriginalJSON), &p); err != nil {
			return apis.NewBadRequestError("اطلاعات خرید خوانده نشد", nil)
		}
		// Everything must come from the signed data, not from the rest of the request.
		if p.PurchaseToken != body.Token || p.ProductID != body.SKU || p.PackageName != body.Package {
			return apis.NewBadRequestError("اطلاعات خرید با هم نمی‌خواند", nil)
		}
		if p.PurchaseState != 0 {
			return apis.NewBadRequestError("این خرید برگشت خورده است", nil)
		}
		if p.DeveloperPayload != e.Auth.Id {
			return apis.NewForbiddenError("این خرید مال حساب دیگری است", nil)
		}
		if body.OrderID == "" {
			body.OrderID = p.OrderID
		}
	} else {
		p, err := validatePurchase(ctx, cfg, body.Package, body.SKU, body.Token)
		if err != nil {
			log.Println("bazaar validate:", err)
			if ce, ok := err.(*callError); ok && ce.Status == http.StatusNotFound {
				return apis.NewBadRequestError("این خرید در بازار پیدا نشد", nil)
			}
			return apis.NewApiError(http.StatusBadGateway, "بررسی خرید با بازار انجام نشد؛ کمی بعد دوباره امتحان کن", nil)
		}
		if p.PurchaseState != 0 {
			return apis.NewBadRequestError("این خرید برگشت خورده است", nil)
		}
		// The app puts the user id in the payload, so a purchase can only ever charge up the account that made it.
		if p.DeveloperPayload != e.Auth.Id {
			return apis.NewForbiddenError("این خرید مال حساب دیگری است", nil)
		}
	}

	tokens := pack.GetFloat("tokens")
	var record *core.Record
	err := app.RunInTransaction(func(tx core.App) error {
		if dup, _ := tx.FindFirstRecordByData("purchases", "token", body.Token); dup != nil {
			return fmt.Errorf("duplicate purchase")
		}
		col, err := tx.FindCollectionByNameOrId("purchases")
		if err != nil {
			return err
		}
		record = core.NewRecord(col)
		record.Set("user", e.Auth.Id)
		record.Set("sku", body.SKU)
		record.Set("token", body.Token)
		record.Set("order_id", body.OrderID)
		record.Set("package", body.Package)
		record.Set("tokens", tokens)
		if err := tx.Save(record); err != nil {
			return err
		}
		user, err := tx.FindRecordById("users", e.Auth.Id)
		if err != nil {
			return err
		}
		user.Set("balance", math.Round((user.GetFloat("balance")+tokens)*10)/10)
		return tx.Save(user)
	})
	if err != nil {
		log.Println("purchase:", err)
		return apis.NewBadRequestError("این خرید قبلاً ثبت شده است", nil)
	}
	finishConsume(app, ctx, cfg, record)
	return answer(tokens, false, record)
}

// handlePurchaseConsumed records that the app consumed the purchase on Bazaar itself.
func handlePurchaseConsumed(e *core.RequestEvent) error {
	var body struct {
		Token string `json:"token"`
	}
	if err := e.BindBody(&body); err != nil || body.Token == "" {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	r, _ := e.App.FindFirstRecordByData("purchases", "token", body.Token)
	if r == nil || r.GetString("user") != e.Auth.Id {
		return apis.NewNotFoundError("خرید پیدا نشد", nil)
	}
	r.Set("consumed", true)
	if err := e.App.Save(r); err != nil {
		return err
	}
	return e.JSON(http.StatusOK, map[string]any{"ok": true})
}

func finishConsume(app core.App, ctx context.Context, cfg bazaarConfig, r *core.Record) {
	if r == nil || r.GetBool("consumed") || cfg.APIToken == "" {
		return
	}
	if err := consumePurchase(ctx, cfg, r.GetString("package"), r.GetString("token")); err != nil {
		log.Println("bazaar consume:", err)
		return
	}
	r.Set("consumed", true)
	_ = app.Save(r)
}
