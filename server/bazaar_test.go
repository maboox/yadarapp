package main

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func fakeBazaar(t *testing.T) *httptest.Server {
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.URL.Path == "/api/v0/tokens":
			_ = r.ParseForm()
			if r.Form.Get("code") != "good" || r.Form.Get("client_secret") != "sec" {
				w.WriteHeader(400)
				return
			}
			_ = json.NewEncoder(w).Encode(map[string]any{"access_token": "AT", "token_type": "Bearer"})
		case r.URL.Path == "/api/v0/userinfo":
			if r.Header.Get("Authorization") != "Bearer AT" {
				w.WriteHeader(401)
				return
			}
			_, _ = w.Write([]byte(`{"account_id":"acc-42"}`))
		case strings.HasPrefix(r.URL.Path, "/devapi/v2/api/validate/"):
			if r.Header.Get("CAFEBAZAAR-PISHKHAN-API-SECRET") != "api" {
				w.WriteHeader(401)
				return
			}
			if strings.Contains(r.URL.Path, "/purchases/missing/") {
				w.WriteHeader(404)
				_, _ = w.Write([]byte(`{"error":"not_found"}`))
				return
			}
			_, _ = w.Write([]byte(`{"consumptionState":1,"purchaseState":0,"developerPayload":"user1","purchaseTime":1}`))
		case strings.HasPrefix(r.URL.Path, "/devapi/v2/api/consume/"):
			var b map[string]string
			_ = json.NewDecoder(r.Body).Decode(&b)
			if b["token"] == "used" {
				w.WriteHeader(400)
				_, _ = w.Write([]byte(`{"error":"already_consumed"}`))
				return
			}
			w.WriteHeader(200)
		default:
			w.WriteHeader(404)
		}
	}))
}

func TestBazaarCalls(t *testing.T) {
	srv := fakeBazaar(t)
	defer srv.Close()
	bazaarTokenURL, bazaarUserInfoURL, bazaarAPIBase = srv.URL+"/api/v0/tokens", srv.URL+"/api/v0/userinfo", srv.URL+"/devapi/v2/api"
	cfg := bazaarConfig{ClientID: "id", ClientSecret: "sec", APIToken: "api"}
	ctx := context.Background()

	if id, err := bazaarAccountID(ctx, cfg, "good"); err != nil || id != "acc-42" {
		t.Fatalf("account id = %q, %v", id, err)
	}
	if _, err := bazaarAccountID(ctx, cfg, "bad"); err == nil {
		t.Fatal("bad code must fail")
	}
	p, err := validatePurchase(ctx, cfg, "com.yadavard.app", "tokens_50", "tok")
	if err != nil || p.PurchaseState != 0 || p.DeveloperPayload != "user1" {
		t.Fatalf("validate = %+v, %v", p, err)
	}
	if _, err := validatePurchase(ctx, cfg, "com.yadavard.app", "tokens_50", "missing"); err == nil {
		t.Fatal("missing purchase must fail")
	} else if ce, ok := err.(*callError); !ok || ce.Status != 404 {
		t.Fatalf("want 404, got %v", err)
	}
	if err := consumePurchase(ctx, cfg, "com.yadavard.app", "tok"); err != nil {
		t.Fatal(err)
	}
	if err := consumePurchase(ctx, cfg, "com.yadavard.app", "used"); err != nil {
		t.Fatal("already consumed counts as done:", err)
	}
}

func TestBazaarPackages(t *testing.T) {
	c := bazaarConfig{Packages: []string{"com.yadavard.app", "com.yadavard.app.plus"}}
	if !c.allows("com.yadavard.app.plus") || c.allows("evil") || c.allows("") {
		t.Fatal("package allow-list")
	}
	if !hex64.MatchString(strings.Repeat("a1", 32)) || hex64.MatchString("xyz") {
		t.Fatal("challenge format")
	}
}
