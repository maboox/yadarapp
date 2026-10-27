package main

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"math"
	"net/http"
	"strconv"
	"sync"
	"time"

	"github.com/pocketbase/pocketbase/apis"
	"github.com/pocketbase/pocketbase/core"
)

// Today's weather for Yadimo's morning hello. The app sends the coordinates of the city the user picked (no
// location permission); answers come from Open-Meteo (free, no key) and are kept for an hour per city.

type weatherAnswer struct {
	Temp float64 `json:"temp"`
	Code int     `json:"code"`
	Max  float64 `json:"max"`
	Min  float64 `json:"min"`
	Rain float64 `json:"rain"`
}

var weatherCache = struct {
	sync.Mutex
	m map[string]struct {
		at time.Time
		w  weatherAnswer
	}
}{m: map[string]struct {
	at time.Time
	w  weatherAnswer
}{}}

func handleWeather(e *core.RequestEvent) error {
	lat, err1 := strconv.ParseFloat(e.Request.URL.Query().Get("lat"), 64)
	lon, err2 := strconv.ParseFloat(e.Request.URL.Query().Get("lon"), 64)
	// Only places in and around Iran, rounded, so the cache stays small and this is no general weather proxy.
	if err1 != nil || err2 != nil || lat < 24 || lat > 41 || lon < 43 || lon > 64 {
		return apis.NewBadRequestError("شهر نامعتبر است", nil)
	}
	lat, lon = math.Round(lat*10)/10, math.Round(lon*10)/10
	key := fmt.Sprintf("%.1f,%.1f", lat, lon)
	weatherCache.Lock()
	hit, ok := weatherCache.m[key]
	weatherCache.Unlock()
	if ok && time.Since(hit.at) < time.Hour {
		return e.JSON(http.StatusOK, hit.w)
	}
	ctx, cancel := context.WithTimeout(e.Request.Context(), 12*time.Second)
	defer cancel()
	w, err := fetchWeather(ctx, lat, lon)
	if err != nil {
		if ok { // an older answer is better than none
			return e.JSON(http.StatusOK, hit.w)
		}
		return apis.NewApiError(http.StatusBadGateway, "آب‌وهوا الان در دسترس نیست", nil)
	}
	weatherCache.Lock()
	if len(weatherCache.m) > 500 {
		weatherCache.m = map[string]struct {
			at time.Time
			w  weatherAnswer
		}{}
	}
	weatherCache.m[key] = struct {
		at time.Time
		w  weatherAnswer
	}{time.Now(), w}
	weatherCache.Unlock()
	return e.JSON(http.StatusOK, w)
}

func fetchWeather(ctx context.Context, lat, lon float64) (weatherAnswer, error) {
	url := fmt.Sprintf("https://api.open-meteo.com/v1/forecast?latitude=%.1f&longitude=%.1f&current=temperature_2m,weather_code"+
		"&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code&timezone=Asia%%2FTehran&forecast_days=1", lat, lon)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return weatherAnswer{}, err
	}
	res, err := http.DefaultClient.Do(req)
	if err != nil {
		return weatherAnswer{}, err
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		return weatherAnswer{}, fmt.Errorf("open-meteo %d", res.StatusCode)
	}
	raw, err := io.ReadAll(io.LimitReader(res.Body, 64<<10))
	if err != nil {
		return weatherAnswer{}, err
	}
	return parseWeather(raw)
}

func parseWeather(raw []byte) (weatherAnswer, error) {
	var data struct {
		Current struct {
			Temp float64 `json:"temperature_2m"`
			Code int     `json:"weather_code"`
		} `json:"current"`
		Daily struct {
			Max  []float64 `json:"temperature_2m_max"`
			Min  []float64 `json:"temperature_2m_min"`
			Rain []float64 `json:"precipitation_probability_max"`
			Code []int     `json:"weather_code"`
		} `json:"daily"`
	}
	if err := json.Unmarshal(raw, &data); err != nil {
		return weatherAnswer{}, err
	}
	if len(data.Daily.Max) == 0 || len(data.Daily.Min) == 0 {
		return weatherAnswer{}, fmt.Errorf("open-meteo: no daily data")
	}
	w := weatherAnswer{Temp: data.Current.Temp, Code: data.Current.Code, Max: data.Daily.Max[0], Min: data.Daily.Min[0]}
	if len(data.Daily.Rain) > 0 {
		w.Rain = data.Daily.Rain[0]
	}
	// The day's code describes the whole day better than the early-morning moment.
	if len(data.Daily.Code) > 0 {
		w.Code = data.Daily.Code[0]
	}
	return w, nil
}
