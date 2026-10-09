package main

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"math"
	"mime/multipart"
	"net/http"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/pocketbase/pocketbase/apis"
	"github.com/pocketbase/pocketbase/core"
)

const (
	maxChatTokens   = 12000
	maxMessageBytes = 400_000
	maxAudioBytes   = 8 << 20
	callTimeout     = 75 * time.Second
)

var httpClient = &http.Client{Timeout: callTimeout}

// provider is one OpenAI-compatible endpoint configured in the admin panel.
type provider struct {
	Name, BaseURL, Key string
	ModelMap           map[string]string
	Extra              map[string]any
}

// modelFor returns the provider's name for a model, or false when the provider is marked as not having it.
func (p provider) modelFor(model string) (string, bool) {
	if m, ok := p.ModelMap[model]; ok {
		m = strings.TrimSpace(m)
		if m == "-" {
			return "", false
		}
		if m != "" {
			return m, true
		}
	}
	return model, true
}

func loadProviders(app core.App) []provider {
	records, err := app.FindAllRecords("providers")
	if err != nil {
		return nil
	}
	sort.SliceStable(records, func(i, j int) bool { return records[i].GetFloat("priority") < records[j].GetFloat("priority") })
	var out []provider
	for _, r := range records {
		base := strings.TrimRight(strings.TrimSpace(r.GetString("base_url")), "/")
		key := strings.TrimSpace(r.GetString("api_key"))
		if !r.GetBool("enabled") || base == "" || key == "" {
			continue
		}
		p := provider{Name: r.GetString("name"), BaseURL: base, Key: key}
		_ = json.Unmarshal([]byte(r.GetString("model_map")), &p.ModelMap)
		_ = json.Unmarshal([]byte(r.GetString("extra_body")), &p.Extra)
		out = append(out, p)
	}
	return out
}

type modelInfo struct {
	Kind                    string
	InPerM, OutPerM, PerMin float64
}

func lookupModel(app core.App, model string, s Settings) modelInfo {
	info := modelInfo{Kind: "chat", InPerM: s.FallbackInputPerM, OutPerM: s.FallbackOutputPerM}
	if r, _ := app.FindFirstRecordByData("models", "model", model); r != nil {
		if k := r.GetString("kind"); k != "" {
			info.Kind = k
		}
		info.InPerM = r.GetFloat("input_per_m")
		info.OutPerM = r.GetFloat("output_per_m")
		info.PerMin = r.GetFloat("per_minute")
	}
	return info
}

// effectiveModel: the user's own model, else their plan's, else the global default.
func effectiveModel(app core.App, user *core.Record, audio bool, s Settings) string {
	field := "text_model"
	def := s.DefaultTextModel
	if audio {
		field = "audio_model"
		def = s.DefaultAudioModel
	}
	if m := strings.TrimSpace(user.GetString(field)); m != "" {
		return m
	}
	if planID := user.GetString("plan"); planID != "" {
		if plan, _ := app.FindRecordById("plans", planID); plan != nil {
			if m := strings.TrimSpace(plan.GetString(field)); m != "" {
				return m
			}
		}
	}
	return def
}

type usage struct {
	Prompt, Completion int
	CostUSD            float64 // reported by the provider, when it does (OpenRouter)
}

// cost in USD of one call; providers that report their own cost win over the price table.
func costUSD(u usage, info modelInfo, audioSeconds float64) float64 {
	if u.CostUSD > 0 {
		return u.CostUSD
	}
	if info.Kind == "transcription" {
		return audioSeconds / 60 * info.PerMin
	}
	return (float64(u.Prompt)*info.InPerM + float64(u.Completion)*info.OutPerM) / 1e6
}

// tokensFor converts a USD cost into app tokens (with markup), rounded up to 0.01.
func tokensFor(cost float64, s Settings) float64 {
	if cost <= 0 {
		return 0
	}
	return math.Ceil(cost*s.Markup/s.TokenUSD*100) / 100
}

// ---- concurrency guard: at most two AI calls per user at a time ----

var (
	inflightMu sync.Mutex
	inflight   = map[string]int{}
)

func acquire(userID string) bool {
	inflightMu.Lock()
	defer inflightMu.Unlock()
	if inflight[userID] >= 2 {
		return false
	}
	inflight[userID]++
	return true
}

func release(userID string) {
	inflightMu.Lock()
	defer inflightMu.Unlock()
	if inflight[userID]--; inflight[userID] <= 0 {
		delete(inflight, userID)
	}
}

// precheck refuses when AI is switched off or the balance is too low; it returns a release func.
func precheck(e *core.RequestEvent, s Settings) (func(), error) {
	if !s.AIEnabled {
		return nil, apis.NewApiError(http.StatusServiceUnavailable, "هوش مصنوعی موقتاً در دسترس نیست", nil)
	}
	user := e.Auth
	if !user.GetBool("unlimited") && user.GetFloat("balance") < math.Max(s.MinBalance, 0.01) {
		// The app recognises this case by the 402 status.
		return nil, apis.NewApiError(http.StatusPaymentRequired, "توکن کافی نداری؛ برای ادامه بسته بخر", nil)
	}
	if !acquire(user.Id) {
		return nil, apis.NewTooManyRequestsError("یک درخواست دیگر هنوز در حال انجام است", nil)
	}
	return func() { release(user.Id) }, nil
}

// charge deducts the tokens and logs the call in one transaction, returning the new balance.
func charge(app core.App, userID, kind, model, providerName string, u usage, seconds, cost, tokens float64) (float64, error) {
	var balance float64
	err := app.RunInTransaction(func(tx core.App) error {
		user, err := tx.FindRecordById("users", userID)
		if err != nil {
			return err
		}
		balance = user.GetFloat("balance")
		if !user.GetBool("unlimited") && tokens > 0 {
			balance = math.Round((balance-tokens)*100) / 100
			user.Set("balance", balance)
			if err := tx.Save(user); err != nil {
				return err
			}
		}
		col, err := tx.FindCollectionByNameOrId("usage")
		if err != nil {
			return err
		}
		r := core.NewRecord(col)
		r.Set("user", userID)
		r.Set("kind", kind)
		r.Set("model", model)
		r.Set("provider", providerName)
		r.Set("prompt_tokens", u.Prompt)
		r.Set("completion_tokens", u.Completion)
		r.Set("audio_seconds", math.Round(seconds*10)/10)
		r.Set("cost_usd", cost)
		r.Set("tokens", tokens)
		return tx.Save(r)
	})
	return balance, err
}

// ---- provider calls ----

type callError struct {
	Status int
	Msg    string
}

func (c *callError) Error() string { return fmt.Sprintf("%d: %s", c.Status, c.Msg) }

func postJSON(ctx context.Context, p provider, path string, body map[string]any) ([]byte, error) {
	payload := map[string]any{}
	for k, v := range p.Extra {
		payload[k] = v
	}
	for k, v := range body {
		payload[k] = v
	}
	data, _ := json.Marshal(payload)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, p.BaseURL+path, bytes.NewReader(data))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+p.Key)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Title", "Yadar")
	return doRequest(req)
}

func doRequest(req *http.Request) ([]byte, error) {
	resp, err := httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	data, _ := io.ReadAll(io.LimitReader(resp.Body, 4<<20))
	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		msg := string(data)
		if len(msg) > 300 {
			msg = msg[:300]
		}
		return nil, &callError{resp.StatusCode, msg}
	}
	return data, nil
}

// parseChat reads the reply text and usage from an OpenAI-style chat completion.
func parseChat(data []byte) (string, usage, error) {
	var res struct {
		Choices []struct {
			Message struct {
				Content json.RawMessage `json:"content"`
			} `json:"message"`
		} `json:"choices"`
		Usage struct {
			Prompt     int     `json:"prompt_tokens"`
			Completion int     `json:"completion_tokens"`
			Cost       float64 `json:"cost"`
		} `json:"usage"`
	}
	if err := json.Unmarshal(data, &res); err != nil {
		return "", usage{}, err
	}
	if len(res.Choices) == 0 {
		return "", usage{}, errors.New("no choices in response")
	}
	u := usage{res.Usage.Prompt, res.Usage.Completion, res.Usage.Cost}
	raw := res.Choices[0].Message.Content
	var text string
	if err := json.Unmarshal(raw, &text); err == nil {
		return text, u, nil
	}
	// Some providers return a list of parts.
	var parts []struct {
		Type string `json:"type"`
		Text string `json:"text"`
	}
	if err := json.Unmarshal(raw, &parts); err != nil {
		return "", u, errors.New("unexpected content format")
	}
	var b strings.Builder
	for _, part := range parts {
		b.WriteString(part.Text)
	}
	return b.String(), u, nil
}

// tryProviders runs call against each provider that has the model until one succeeds.
func tryProviders(app core.App, model string, call func(p provider, providerModel string) error) (string, error) {
	providers := loadProviders(app)
	if len(providers) == 0 {
		return "", errors.New("no AI provider is configured")
	}
	var errs []string
	for _, p := range providers {
		pm, ok := p.modelFor(model)
		if !ok {
			continue
		}
		err := call(p, pm)
		if err == nil {
			return p.Name, nil
		}
		errs = append(errs, p.Name+": "+err.Error())
	}
	if len(errs) == 0 {
		return "", fmt.Errorf("no provider offers %s", model)
	}
	return "", errors.New(strings.Join(errs, " | "))
}

func unavailable(err error) error {
	log.Println("ai:", err)
	return apis.NewApiError(http.StatusBadGateway, "سرویس هوش مصنوعی الان جواب نداد؛ کمی بعد دوباره امتحان کن", nil)
}

// ---- handlers ----

func handleChat(e *core.RequestEvent) error {
	var body struct {
		Messages    json.RawMessage `json:"messages"`
		MaxTokens   int             `json:"max_tokens"`
		Temperature *float64        `json:"temperature"`
	}
	if err := e.BindBody(&body); err != nil {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	var messages []map[string]any
	if len(body.Messages) > maxMessageBytes || json.Unmarshal(body.Messages, &messages) != nil || len(messages) == 0 || len(messages) > 20 {
		return apis.NewBadRequestError("پیام نامعتبر است", nil)
	}
	app := e.App
	s := loadSettings(app)
	done, err := precheck(e, s)
	if err != nil {
		return err
	}
	defer done()

	maxTokens := body.MaxTokens
	if maxTokens <= 0 || maxTokens > maxChatTokens {
		maxTokens = maxChatTokens
	}
	temperature := 0.0
	if body.Temperature != nil {
		temperature = math.Max(0, math.Min(*body.Temperature, 1.5))
	}
	model := effectiveModel(app, e.Auth, false, s)
	info := lookupModel(app, model, s)

	var content string
	var u usage
	ctx, cancel := context.WithTimeout(e.Request.Context(), 2*callTimeout)
	defer cancel()
	providerName, err := tryProviders(app, model, func(p provider, pm string) error {
		data, err := postJSON(ctx, p, "/chat/completions", map[string]any{
			"model": pm, "messages": messages, "max_tokens": maxTokens, "temperature": temperature})
		if err != nil {
			return err
		}
		content, u, err = parseChat(data)
		return err
	})
	if err != nil {
		return unavailable(err)
	}
	cost := costUSD(u, info, 0)
	tokens := tokensFor(cost, s)
	balance, err := charge(app, e.Auth.Id, "chat", model, providerName, u, 0, cost, tokens)
	if err != nil {
		return err
	}
	return e.JSON(http.StatusOK, result(e, content, "content", tokens, balance))
}

func result(e *core.RequestEvent, value, key string, tokens, balance float64) map[string]any {
	unlimited := e.Auth.GetBool("unlimited")
	if unlimited {
		tokens = 0
	}
	return map[string]any{key: value, "charged": tokens, "balance": balance, "unlimited": unlimited}
}

// wavSeconds reads the duration from a PCM WAV header (falls back to 16 kHz mono 16-bit).
func wavSeconds(wav []byte) float64 {
	byteRate := 32000.0
	if len(wav) >= 44 && string(wav[0:4]) == "RIFF" && string(wav[8:12]) == "WAVE" {
		if br := binary.LittleEndian.Uint32(wav[28:32]); br > 0 {
			byteRate = float64(br)
		}
	}
	n := len(wav) - 44
	if n < 0 {
		n = 0
	}
	return float64(n) / byteRate
}

const transcribeInstruction = "Transcribe this voice note exactly as spoken, in its original language (usually Persian). " +
	"Reply with the transcript only, no quotes or explanations."

func handleTranscribe(e *core.RequestEvent) error {
	var body struct {
		Audio    string `json:"audio"`
		Language string `json:"language"`
	}
	if err := e.BindBody(&body); err != nil {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	wav, err := base64.StdEncoding.DecodeString(body.Audio)
	if err != nil || len(wav) < 1000 || len(wav) > maxAudioBytes {
		return apis.NewBadRequestError("فایل صدا نامعتبر یا خیلی بزرگ است", nil)
	}
	lang := body.Language
	if lang != "en" {
		lang = "fa"
	}
	app := e.App
	s := loadSettings(app)
	done, err := precheck(e, s)
	if err != nil {
		return err
	}
	defer done()

	model := effectiveModel(app, e.Auth, true, s)
	info := lookupModel(app, model, s)
	seconds := wavSeconds(wav)
	ctx, cancel := context.WithTimeout(e.Request.Context(), 2*callTimeout)
	defer cancel()

	var text string
	var u usage
	providerName, err := tryProviders(app, model, func(p provider, pm string) error {
		if info.Kind == "transcription" {
			t, err := transcribeEndpoint(ctx, p, pm, wav, lang)
			text = t
			return err
		}
		data, err := postJSON(ctx, p, "/chat/completions", map[string]any{
			"model": pm, "temperature": 0, "max_tokens": 1000,
			"messages": []map[string]any{{"role": "user", "content": []map[string]any{
				{"type": "text", "text": transcribeInstruction},
				{"type": "input_audio", "input_audio": map[string]any{"data": body.Audio, "format": "wav"}},
			}}}})
		if err != nil {
			return err
		}
		text, u, err = parseChat(data)
		return err
	})
	if err != nil {
		return unavailable(err)
	}
	text = strings.TrimSpace(text)
	cost := costUSD(u, info, seconds)
	tokens := tokensFor(cost, s)
	balance, err := charge(app, e.Auth.Id, "voice", model, providerName, u, seconds, cost, tokens)
	if err != nil {
		return err
	}
	return e.JSON(http.StatusOK, result(e, text, "text", tokens, balance))
}

// transcribeEndpoint uses the OpenAI-style /audio/transcriptions (Whisper and similar models).
func transcribeEndpoint(ctx context.Context, p provider, model string, wav []byte, lang string) (string, error) {
	var buf bytes.Buffer
	w := multipart.NewWriter(&buf)
	_ = w.WriteField("model", model)
	_ = w.WriteField("language", lang)
	part, _ := w.CreateFormFile("file", "voice.wav")
	_, _ = part.Write(wav)
	_ = w.Close()
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, p.BaseURL+"/audio/transcriptions", &buf)
	if err != nil {
		return "", err
	}
	req.Header.Set("Authorization", "Bearer "+p.Key)
	req.Header.Set("Content-Type", w.FormDataContentType())
	data, err := doRequest(req)
	if err != nil {
		return "", err
	}
	var res struct {
		Text string `json:"text"`
	}
	if err := json.Unmarshal(data, &res); err != nil {
		return "", err
	}
	return res.Text, nil
}

// ---- provider model check (admin panel: tick "check_now" and save) ----

func checkProvider(app core.App, r *core.Record) string {
	base := strings.TrimRight(strings.TrimSpace(r.GetString("base_url")), "/")
	key := strings.TrimSpace(r.GetString("api_key"))
	if base == "" || key == "" {
		return "آدرس یا کلید خالی است"
	}
	req, _ := http.NewRequest(http.MethodGet, base+"/models", nil)
	req.Header.Set("Authorization", "Bearer "+key)
	data, err := doRequest(req)
	if err != nil {
		return "خطا در اتصال: " + err.Error()
	}
	var res struct {
		Data []struct {
			ID string `json:"id"`
		} `json:"data"`
	}
	if err := json.Unmarshal(data, &res); err != nil {
		return "پاسخ نامفهوم از /models"
	}
	available := map[string]bool{}
	for _, m := range res.Data {
		available[m.ID] = true
	}
	p := provider{}
	_ = json.Unmarshal([]byte(r.GetString("model_map")), &p.ModelMap)

	s := loadSettings(app)
	wanted := map[string]bool{s.DefaultTextModel: true, s.DefaultAudioModel: true}
	if plans, err := app.FindAllRecords("plans"); err == nil {
		for _, pl := range plans {
			wanted[pl.GetString("text_model")] = true
			wanted[pl.GetString("audio_model")] = true
		}
	}
	if models, err := app.FindAllRecords("models"); err == nil {
		for _, m := range models {
			wanted[m.GetString("model")] = true
		}
	}
	delete(wanted, "")
	names := make([]string, 0, len(wanted))
	for m := range wanted {
		names = append(names, m)
	}
	sort.Strings(names)
	var b strings.Builder
	fmt.Fprintf(&b, "%s — %d مدل در این سرویس\n", time.Now().Format("2006-01-02 15:04"), len(res.Data))
	for _, m := range names {
		pm, ok := p.modelFor(m)
		switch {
		case !ok:
			fmt.Fprintf(&b, "– %s (در model_map غیرفعال)\n", m)
		case available[pm]:
			fmt.Fprintf(&b, "✓ %s\n", m)
		default:
			// Suggest similarly named models to fill model_map.
			var similar []string
			short := m[strings.LastIndex(m, "/")+1:]
			for id := range available {
				if strings.Contains(strings.ToLower(id), strings.ToLower(short)) && len(similar) < 3 {
					similar = append(similar, id)
				}
			}
			fmt.Fprintf(&b, "✗ %s", m)
			if len(similar) > 0 {
				fmt.Fprintf(&b, " — شاید: %s", strings.Join(similar, "، "))
			}
			b.WriteString("\n")
		}
	}
	return b.String()
}
