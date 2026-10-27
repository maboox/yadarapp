package main

import "testing"

func TestParseWeather(t *testing.T) {
	raw := []byte(`{"current":{"temperature_2m":14.2,"weather_code":1},"daily":{"temperature_2m_max":[22.5],"temperature_2m_min":[11],"precipitation_probability_max":[30],"weather_code":[61]}}`)
	w, err := parseWeather(raw)
	if err != nil {
		t.Fatal(err)
	}
	if w.Temp != 14.2 || w.Max != 22.5 || w.Min != 11 || w.Rain != 30 || w.Code != 61 {
		t.Fatalf("unexpected %+v", w)
	}
	if _, err := parseWeather([]byte(`{"daily":{}}`)); err == nil {
		t.Fatal("empty daily data must fail")
	}
}

func TestYadimoTiers(t *testing.T) {
	for id, tier := range yadimoTiers {
		if _, ok := yadimoDefaultPrices[tier]; !ok && tier != "free" {
			t.Errorf("%s has unknown tier %q", id, tier)
		}
	}
	if trimNum(5) != "5" || trimNum(2.5) != "2.5" {
		t.Fatal("trimNum")
	}
}
