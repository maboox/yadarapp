package main

import (
	"encoding/json"
	"fmt"
	"math"
	"net/http"
	"strings"
	"unicode/utf8"

	"github.com/pocketbase/pocketbase/apis"
	"github.com/pocketbase/pocketbase/core"
)

// Yadimo's wardrobe. Every part has a price tier; the price of each tier is set in the admin panel (settings →
// yadimo_price_*), so prices change without a new app. Ids are "<kind>:<id>" as the app's engine names them.
// Parts that are not listed here are free (the characters, colours and faces).
var yadimoTiers = map[string]string{
	"hat:party": "free", "glass:round": "free",

	"hat:cap": "basic", "hat:beanie": "basic", "hat:night": "basic", "hat:bow": "basic", "hat:chef": "basic",
	"glass:sun": "basic", "neck:bowtie": "basic", "neck:scarf": "basic", "neck:tie": "basic",
	"item:cup": "basic", "item:phone": "basic", "item:flag": "basic",

	"hat:crown": "special", "hat:wizard": "special", "hat:phones": "special", "hat:grad": "special", "hat:flowers": "special",
	"glass:star": "special", "glass:heart": "special", "neck:medal": "special", "item:wand": "special", "item:balloon": "special",
	"titem:hammer": "special", "titem:shield": "special", "titem:ball": "special", "titem:saber": "special", "titem:staff": "special",

	"costume:bat": "costume", "costume:plumber": "costume", "costume:spider": "costume", "costume:iron": "costume",
	"costume:straw": "costume", "costume:ninja": "costume", "costume:ice": "costume", "costume:thor": "costume",
	"costume:elf": "costume", "costume:graywiz": "costume", "costume:joker": "costume", "costume:wed": "costume",
	"costume:saiyan": "costume", "costume:hero": "costume", "costume:pirate": "costume",
}

// Starting prices of the tiers (tokens); written into settings once, then edited in the admin panel.
var yadimoDefaultPrices = map[string]float64{"basic": 5, "special": 10, "costume": 20}

func yadimoPrices(app core.App) map[string]float64 {
	out := map[string]float64{"free": 0}
	for tier, def := range yadimoDefaultPrices {
		out[tier] = def
	}
	if records, err := app.FindAllRecords("settings"); err == nil && len(records) > 0 {
		for tier := range yadimoDefaultPrices {
			if f := records[0].Get("yadimo_price_" + tier); f != nil {
				out[tier] = math.Max(0, records[0].GetFloat("yadimo_price_"+tier))
			}
		}
	}
	return out
}

func yadimoOwned(user *core.Record) []string {
	var owned []string
	_ = json.Unmarshal([]byte(user.GetString("yadimo_owned")), &owned)
	return owned
}

// yadimoState is everything the app needs for the wardrobe: tier of each part, tier prices, what this user owns
// and the look saved on the account.
func yadimoState(app core.App, user *core.Record) map[string]any {
	var look any
	if raw := user.GetString("yadimo_look"); raw != "" && raw != "null" {
		_ = json.Unmarshal([]byte(raw), &look)
	}
	owned := yadimoOwned(user)
	if owned == nil {
		owned = []string{}
	}
	return map[string]any{
		"tiers": yadimoTiers, "prices": yadimoPrices(app), "owned": owned, "look": look,
		"balance": user.GetFloat("balance"), "unlimited": user.GetBool("unlimited"),
	}
}

func handleYadimo(e *core.RequestEvent) error {
	user, err := e.App.FindRecordById("users", e.Auth.Id)
	if err != nil {
		return err
	}
	return e.JSON(http.StatusOK, yadimoState(e.App, user))
}

// handleYadimoBuy takes the tier price from the balance and adds the part to the account, once.
func handleYadimoBuy(e *core.RequestEvent) error {
	var body struct {
		Item string `json:"item"`
	}
	if err := e.BindBody(&body); err != nil {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	tier, ok := yadimoTiers[body.Item]
	if !ok {
		return apis.NewBadRequestError("این وسیله شناخته نشد", nil)
	}
	price := yadimoPrices(e.App)[tier]
	var user *core.Record
	err := e.App.RunInTransaction(func(tx core.App) error {
		u, err := tx.FindRecordById("users", e.Auth.Id)
		if err != nil {
			return err
		}
		user = u
		owned := yadimoOwned(u)
		for _, id := range owned {
			if id == body.Item {
				return nil // already bought: nothing to charge
			}
		}
		charge := price
		if u.GetBool("unlimited") {
			charge = 0
		}
		if charge > 0 && u.GetFloat("balance")+1e-9 < charge {
			return apis.NewApiError(http.StatusPaymentRequired, fmt.Sprintf("موجودی کافی نیست؛ این وسیله %s توکن است", trimNum(price)), nil)
		}
		raw, _ := json.Marshal(append(owned, body.Item))
		u.Set("yadimo_owned", string(raw))
		u.Set("balance", math.Round((u.GetFloat("balance")-charge)*10)/10)
		if err := tx.Save(u); err != nil {
			return err
		}
		if charge > 0 {
			col, err := tx.FindCollectionByNameOrId("usage")
			if err != nil {
				return err
			}
			r := core.NewRecord(col)
			r.Set("user", u.Id)
			r.Set("kind", "yadimo")
			r.Set("model", body.Item)
			r.Set("tokens", charge)
			return tx.Save(r)
		}
		return nil
	})
	if err != nil {
		return err
	}
	return e.JSON(http.StatusOK, yadimoState(e.App, user))
}

// handleYadimoLook saves the chosen look on the account so it comes back after signing in on another phone.
// Parts the user does not own are dropped.
func handleYadimoLook(e *core.RequestEvent) error {
	var look map[string]string
	if err := e.BindBody(&look); err != nil || len(look) > 20 {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	user, err := e.App.FindRecordById("users", e.Auth.Id)
	if err != nil {
		return err
	}
	owned := map[string]bool{}
	for _, id := range yadimoOwned(user) {
		owned[id] = true
	}
	kinds := map[string]string{"hat": "hat", "glass": "glass", "neck": "neck", "costume": "costume"}
	clean := map[string]string{}
	for k, v := range look {
		if utf8.RuneCountInString(k) > 20 || utf8.RuneCountInString(v) > 30 || strings.ContainsAny(v, "<>\"") {
			continue
		}
		id := ""
		if kind, ok := kinds[k]; ok {
			id = kind + ":" + v
		} else if k == "item" {
			id = "item:" + v
			if _, ok := yadimoTiers[id]; !ok {
				id = "titem:" + v
			}
		}
		if id != "" && v != "none" && yadimoTiers[id] != "" && yadimoTiers[id] != "free" && !owned[id] && !user.GetBool("unlimited") {
			v = "none"
		}
		clean[k] = v
	}
	raw, _ := json.Marshal(clean)
	user.Set("yadimo_look", string(raw))
	if err := e.App.Save(user); err != nil {
		return err
	}
	return e.JSON(http.StatusOK, yadimoState(e.App, user))
}

func trimNum(v float64) string {
	if v == math.Trunc(v) {
		return fmt.Sprintf("%d", int64(v))
	}
	return fmt.Sprintf("%.1f", v)
}
