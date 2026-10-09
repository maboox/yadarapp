package main

import (
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"math/big"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"
	"unicode"

	"github.com/pocketbase/dbx"
	"github.com/pocketbase/pocketbase/apis"
	"github.com/pocketbase/pocketbase/core"
	"github.com/pocketbase/pocketbase/tools/security"
)

const (
	otpLength   = 5
	otpLifetime = 3 * time.Minute
	otpMaxTries = 5
)

// normalizePhone turns any common way of writing an Iranian mobile number (Persian digits, +98, 0098, 98…)
// into 09xxxxxxxxx, or returns "" when it is not one.
func normalizePhone(raw string) string {
	var b strings.Builder
	for _, r := range raw {
		switch {
		case r >= '0' && r <= '9':
			b.WriteRune(r)
		case r >= '۰' && r <= '۹':
			b.WriteRune('0' + (r - '۰'))
		case r >= '٠' && r <= '٩':
			b.WriteRune('0' + (r - '٠'))
		case r == '+' || unicode.IsSpace(r) || r == '-' || r == '(' || r == ')':
		default:
			return ""
		}
	}
	d := b.String()
	switch {
	case strings.HasPrefix(d, "0098"):
		d = "0" + d[4:]
	case strings.HasPrefix(d, "98") && len(d) == 12:
		d = "0" + d[2:]
	case strings.HasPrefix(d, "9") && len(d) == 10:
		d = "0" + d
	}
	if len(d) != 11 || !strings.HasPrefix(d, "09") {
		return ""
	}
	return d
}

func hashCode(phone, code string) string {
	sum := sha256.Sum256([]byte(os.Getenv("OTP_SECRET") + "|" + phone + "|" + code))
	return hex.EncodeToString(sum[:])
}

func randomCode() string {
	max := big.NewInt(1)
	for i := 0; i < otpLength; i++ {
		max.Mul(max, big.NewInt(10))
	}
	n, err := rand.Int(rand.Reader, max)
	if err != nil {
		panic(err)
	}
	return fmt.Sprintf("%0*d", otpLength, n.Int64())
}

// reviewCode lets store reviewers (and the admin) log in with a fixed code on one test number, without SMS.
func reviewCode(phone string) string {
	if p := normalizePhone(os.Getenv("REVIEW_PHONE")); p != "" && p == phone {
		return strings.TrimSpace(os.Getenv("REVIEW_CODE"))
	}
	return ""
}

func handleOTPRequest(e *core.RequestEvent) error {
	var body struct {
		Phone string `json:"phone"`
	}
	if err := e.BindBody(&body); err != nil {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	phone := normalizePhone(body.Phone)
	if phone == "" {
		return apis.NewBadRequestError("شمارهٔ موبایل درست نیست", nil)
	}
	app := e.App
	now := time.Now()
	recent, _ := app.CountRecords("otps", dbx.NewExp("phone = {:p} AND created > {:t}",
		dbx.Params{"p": phone, "t": now.Add(-time.Hour).UTC().Format("2006-01-02 15:04:05.000Z")}))
	if recent >= 5 {
		return apis.NewTooManyRequestsError("تعداد درخواست کد زیاد بود؛ کمی بعد دوباره امتحان کن", nil)
	}
	last, _ := app.FindRecordsByFilter("otps", "phone = {:p}", "-created", 1, 0, dbx.Params{"p": phone})
	if len(last) > 0 && now.Sub(last[0].GetDateTime("created").Time()) < 60*time.Second {
		return apis.NewTooManyRequestsError("یک دقیقه صبر کن و دوباره کد بخواه", nil)
	}

	code := reviewCode(phone)
	if code == "" {
		code = randomCode()
	}
	otps, err := app.FindCollectionByNameOrId("otps")
	if err != nil {
		return err
	}
	r := core.NewRecord(otps)
	r.Set("phone", phone)
	r.Set("code_hash", hashCode(phone, code))
	r.Set("expires", now.Add(otpLifetime).UnixMilli())
	r.Set("attempts", 0)
	r.Set("ip", e.RealIP())
	if err := app.Save(r); err != nil {
		return err
	}

	result := map[string]any{"ok": true, "length": len(code), "expires_in": int(otpLifetime.Seconds())}
	if reviewCode(phone) == "" {
		if err := sendSMS(phone, code); err != nil {
			log.Println("sms:", err)
			return apis.NewApiError(http.StatusBadGateway, "ارسال پیامک انجام نشد؛ کمی بعد دوباره امتحان کن", nil)
		}
		if strings.EqualFold(os.Getenv("SMS_PROVIDER"), "dev") {
			result["dev_code"] = code // only for local testing
		}
	}
	return e.JSON(http.StatusOK, result)
}

func handleOTPVerify(e *core.RequestEvent) error {
	var body struct {
		Phone string `json:"phone"`
		Code  string `json:"code"`
	}
	if err := e.BindBody(&body); err != nil {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	phone := normalizePhone(body.Phone)
	code := digitsOnly(body.Code)
	if phone == "" || len(code) != otpLength {
		return apis.NewBadRequestError("کد را کامل وارد کن", nil)
	}
	app := e.App
	list, _ := app.FindRecordsByFilter("otps", "phone = {:p}", "-created", 1, 0, dbx.Params{"p": phone})
	if len(list) == 0 {
		return apis.NewBadRequestError("اول کد را درخواست کن", nil)
	}
	otp := list[0]
	if time.Now().UnixMilli() > int64(otp.GetFloat("expires")) {
		return apis.NewBadRequestError("کد منقضی شده؛ دوباره کد بخواه", nil)
	}
	if otp.GetInt("attempts") >= otpMaxTries {
		return apis.NewTooManyRequestsError("تلاش‌ها زیاد بود؛ دوباره کد بخواه", nil)
	}
	if subtle.ConstantTimeCompare([]byte(otp.GetString("code_hash")), []byte(hashCode(phone, code))) != 1 {
		otp.Set("attempts", otp.GetInt("attempts")+1)
		_ = app.Save(otp)
		return apis.NewBadRequestError("کد اشتباه است", nil)
	}
	// One code, one login.
	old, _ := app.FindRecordsByFilter("otps", "phone = {:p}", "", 0, 0, dbx.Params{"p": phone})
	for _, r := range old {
		_ = app.Delete(r)
	}

	user, created, err := findOrCreateUser(app, phone)
	if err != nil {
		return err
	}
	token, err := user.NewAuthToken()
	if err != nil {
		return err
	}
	return e.JSON(http.StatusOK, map[string]any{"token": token, "new": created, "profile": profile(app, user)})
}

func digitsOnly(s string) string {
	var b strings.Builder
	for _, r := range s {
		switch {
		case r >= '0' && r <= '9':
			b.WriteRune(r)
		case r >= '۰' && r <= '۹':
			b.WriteRune('0' + (r - '۰'))
		case r >= '٠' && r <= '٩':
			b.WriteRune('0' + (r - '٠'))
		}
	}
	return b.String()
}

func findOrCreateUser(app core.App, phone string) (*core.Record, bool, error) {
	if u, _ := app.FindFirstRecordByData("users", "phone", phone); u != nil {
		return u, false, nil
	}
	users, err := app.FindCollectionByNameOrId("users")
	if err != nil {
		return nil, false, err
	}
	u := core.NewRecord(users)
	u.Set("phone", phone)
	u.SetEmail(phone + "@phone.yadar.app")
	u.SetPassword(security.RandomString(40))
	u.SetVerified(true)
	u.Set("balance", loadSettings(app).SignupTokens)
	if err := app.Save(u); err != nil {
		return nil, false, err
	}
	return u, true, nil
}

// sendSMS delivers the login code through the provider chosen with SMS_PROVIDER (kavenegar, smsir or dev).
func sendSMS(phone, code string) error {
	client := &http.Client{Timeout: 15 * time.Second}
	switch strings.ToLower(os.Getenv("SMS_PROVIDER")) {
	case "kavenegar":
		// Verify lookup: the template (approved in Kavenegar's panel) contains %token.
		u := fmt.Sprintf("https://api.kavenegar.com/v1/%s/verify/lookup.json?receptor=%s&token=%s&template=%s",
			url.PathEscape(os.Getenv("KAVENEGAR_API_KEY")), url.QueryEscape(phone), url.QueryEscape(code),
			url.QueryEscape(os.Getenv("KAVENEGAR_TEMPLATE")))
		resp, err := client.Get(u)
		if err != nil {
			return err
		}
		defer resp.Body.Close()
		data, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
		if resp.StatusCode != http.StatusOK {
			return fmt.Errorf("kavenegar %d: %s", resp.StatusCode, string(data))
		}
		return nil
	case "smsir":
		// sms.ir verify: the template (approved in sms.ir's panel) has one parameter, by default named CODE.
		param := os.Getenv("SMSIR_PARAM")
		if param == "" {
			param = "CODE"
		}
		var templateID int
		fmt.Sscan(os.Getenv("SMSIR_TEMPLATE_ID"), &templateID)
		payload, _ := json.Marshal(map[string]any{"mobile": phone, "templateId": templateID,
			"parameters": []map[string]string{{"name": param, "value": code}}})
		req, _ := http.NewRequest(http.MethodPost, "https://api.sms.ir/v1/send/verify", strings.NewReader(string(payload)))
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Accept", "application/json")
		req.Header.Set("x-api-key", os.Getenv("SMSIR_API_KEY"))
		resp, err := client.Do(req)
		if err != nil {
			return err
		}
		defer resp.Body.Close()
		data, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
		if resp.StatusCode != http.StatusOK {
			return fmt.Errorf("sms.ir %d: %s", resp.StatusCode, string(data))
		}
		return nil
	case "dev":
		log.Printf("DEV SMS to %s: code %s", phone, code)
		return nil
	default:
		return fmt.Errorf("SMS_PROVIDER is not set (kavenegar, smsir or dev)")
	}
}
