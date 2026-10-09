package main

import (
	"encoding/binary"
	"math"
	"testing"
)

func TestNormalizePhone(t *testing.T) {
	cases := map[string]string{
		"09121234567":      "09121234567",
		"۰۹۱۲۱۲۳۴۵۶۷":      "09121234567",
		"+98 912 123 4567": "09121234567",
		"00989121234567":   "09121234567",
		"989121234567":     "09121234567",
		"9121234567":       "09121234567",
		"0912123456":       "",
		"02112345678":      "",
		"0912abc4567":      "",
		"":                 "",
	}
	for in, want := range cases {
		if got := normalizePhone(in); got != want {
			t.Errorf("normalizePhone(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestDigitsAndCode(t *testing.T) {
	if got := digitsOnly(" ۱۲-۳۴٥ "); got != "12345" {
		t.Fatalf("digitsOnly = %q", got)
	}
	for i := 0; i < 50; i++ {
		if c := randomCode(); len(c) != otpLength {
			t.Fatalf("code %q has wrong length", c)
		}
	}
	if hashCode("09121234567", "12345") == hashCode("09121234568", "12345") {
		t.Fatal("hash must depend on the phone")
	}
}

func TestCostAndTokens(t *testing.T) {
	s := Settings{TokenUSD: 0.001, Markup: 1.5}
	chat := modelInfo{Kind: "chat", InPerM: 0.30, OutPerM: 2.50}
	// 2000 prompt + 300 completion tokens = 0.0006 + 0.00075 = 0.00135 USD → ×1.5 / 0.001 = 2.025 → 2.03
	cost := costUSD(usage{Prompt: 2000, Completion: 300}, chat, 0)
	if math.Abs(cost-0.00135) > 1e-12 {
		t.Fatalf("cost = %v", cost)
	}
	if got := tokensFor(cost, s); got != 2.03 {
		t.Fatalf("tokens = %v", got)
	}
	// A provider-reported cost wins over the price table.
	if got := costUSD(usage{Prompt: 2000, CostUSD: 0.01}, chat, 0); got != 0.01 {
		t.Fatalf("reported cost ignored: %v", got)
	}
	whisper := modelInfo{Kind: "transcription", PerMin: 0.006}
	if got := costUSD(usage{}, whisper, 30); math.Abs(got-0.003) > 1e-12 {
		t.Fatalf("audio cost = %v", got)
	}
	if tokensFor(0, s) != 0 {
		t.Fatal("free call must cost nothing")
	}
}

func TestProviderModelMap(t *testing.T) {
	p := provider{ModelMap: map[string]string{"a/x": "x", "b/y": "-", "c/z": ""}}
	if m, ok := p.modelFor("a/x"); !ok || m != "x" {
		t.Fatal("mapped name")
	}
	if _, ok := p.modelFor("b/y"); ok {
		t.Fatal("'-' must disable the model")
	}
	if m, ok := p.modelFor("c/z"); !ok || m != "c/z" {
		t.Fatal("empty mapping keeps the name")
	}
	if m, ok := p.modelFor("d/w"); !ok || m != "d/w" {
		t.Fatal("unmapped keeps the name")
	}
}

func TestParseChat(t *testing.T) {
	text, u, err := parseChat([]byte(`{"choices":[{"message":{"content":"سلام"}}],"usage":{"prompt_tokens":10,"completion_tokens":2}}`))
	if err != nil || text != "سلام" || u.Prompt != 10 || u.Completion != 2 {
		t.Fatalf("string content: %q %+v %v", text, u, err)
	}
	text, _, err = parseChat([]byte(`{"choices":[{"message":{"content":[{"type":"text","text":"a"},{"type":"text","text":"b"}]}}]}`))
	if err != nil || text != "ab" {
		t.Fatalf("parts content: %q %v", text, err)
	}
	if _, _, err := parseChat([]byte(`{"choices":[]}`)); err == nil {
		t.Fatal("empty choices must fail")
	}
}

func TestWavSeconds(t *testing.T) {
	wav := make([]byte, 44+32000*3)
	copy(wav[0:], "RIFF")
	copy(wav[8:], "WAVE")
	binary.LittleEndian.PutUint32(wav[28:], 32000)
	if got := wavSeconds(wav); got != 3 {
		t.Fatalf("seconds = %v", got)
	}
}
