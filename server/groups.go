package main

import (
	"encoding/json"
	"net/http"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/pocketbase/dbx"
	"github.com/pocketbase/pocketbase/apis"
	"github.com/pocketbase/pocketbase/core"
)

/*
 Groups: shared reminders among a few people (a family, a team). Someone makes a group, invites others by their
 username, and those who accept see the group's reminders on their phones. Only what a group has now is kept: its
 members, its reminders (encrypted with a key of the group) and who did which one; finished one-off reminders and
 old news go after 30 days. Phones ask for what changed every so often; nothing is pushed to them.
*/

const (
	groupMaxMembers  = 20 // members and pending invitations of one group
	groupMaxPerUser  = 5  // groups one person is in
	groupInvitesADay = 20 // invitations one person sends a day
	groupKeep        = 30 * 24 * time.Hour
)

func groupKey(app core.App, id string) []byte { return deriveKey(serverSecret(app), "group|"+id) }

// membership returns the user's row in the group, or nil.
func membership(app core.App, groupID, userID string) *core.Record {
	r, _ := app.FindFirstRecordByFilter("group_members", "grp = {:g} && user = {:u}", dbx.Params{"g": groupID, "u": userID})
	return r
}

func activeMember(e *core.RequestEvent) (*core.Record, *core.Record, error) {
	g, err := e.App.FindRecordById("groups", e.Request.PathValue("id"))
	if err != nil {
		return nil, nil, apis.NewNotFoundError("گروه پیدا نشد", nil)
	}
	m := membership(e.App, g.Id, e.Auth.Id)
	if m == nil || m.GetString("status") != "active" {
		return nil, nil, apis.NewForbiddenError("عضو این گروه نیستی", nil)
	}
	return g, m, nil
}

func countActiveGroups(app core.App, userID string) int64 {
	n, _ := app.CountRecords("group_members", dbx.NewExp("user = {:u} AND status = 'active'", dbx.Params{"u": userID}))
	return n
}

func addEvent(app core.App, groupID, kind, actor, target, text string) {
	col, err := app.FindCollectionByNameOrId("group_events")
	if err != nil {
		return
	}
	r := core.NewRecord(col)
	r.Set("grp", groupID)
	r.Set("kind", kind)
	r.Set("actor", actor)
	r.Set("target", target)
	if sealed, err := seal(groupKey(app, groupID), text); err == nil {
		r.Set("text", sealed)
	}
	r.Set("at", nowMs())
	_ = app.Save(r)
}

func displayName(u *core.Record) string {
	if u == nil {
		return ""
	}
	if n := strings.TrimSpace(u.GetString("name")); n != "" {
		return n
	}
	if n := u.GetString("username"); n != "" {
		return "@" + n
	}
	return "?"
}

// groupView is a group as the phone sees it, with its members.
func groupView(app core.App, g *core.Record, me string) map[string]any {
	rows, _ := app.FindRecordsByFilter("group_members", "grp = {:g}", "created", 100, 0, dbx.Params{"g": g.Id})
	members := []map[string]any{}
	role := ""
	for _, m := range rows {
		u, _ := app.FindRecordById("users", m.GetString("user"))
		if u == nil {
			continue
		}
		if u.Id == me {
			role = m.GetString("role")
		}
		members = append(members, map[string]any{"id": u.Id, "name": displayName(u), "username": u.GetString("username"),
			"role": m.GetString("role"), "status": m.GetString("status")})
	}
	owner, _ := app.FindRecordById("users", g.GetString("owner"))
	return map[string]any{"id": g.Id, "name": g.GetString("name"), "owner": g.GetString("owner"), "owner_name": displayName(owner),
		"edit_mode": g.GetString("edit_mode"), "role": role, "members": members}
}

// handleGroupsSync: my groups, my invitations, and the reminders, done marks and news changed since ?since=.
func handleGroupsSync(e *core.RequestEvent) error {
	app := e.App
	me := e.Auth.Id
	since, _ := strconv.ParseInt(e.Request.URL.Query().Get("since"), 10, 64)
	now := nowMs()
	cleanupGroups(app)
	rows, err := app.FindRecordsByFilter("group_members", "user = {:u}", "created", 100, 0, dbx.Params{"u": me})
	if err != nil {
		return err
	}
	groups, invites, ids := []map[string]any{}, []map[string]any{}, []string{}
	for _, m := range rows {
		g, err := app.FindRecordById("groups", m.GetString("grp"))
		if err != nil {
			continue
		}
		v := groupView(app, g, me)
		if m.GetString("status") == "active" {
			groups = append(groups, v)
			ids = append(ids, g.Id)
		} else {
			if inviter, _ := app.FindRecordById("users", m.GetString("invited_by")); inviter != nil {
				v["invited_by"] = displayName(inviter)
			}
			invites = append(invites, v)
		}
	}
	items, done, events := []map[string]any{}, []map[string]any{}, []map[string]any{}
	for _, gid := range ids {
		key := groupKey(app, gid)
		recs, _ := app.FindRecordsByFilter("group_items", "grp = {:g} && at >= {:s}", "at", 2000, 0, dbx.Params{"g": gid, "s": since - 5000})
		for _, r := range recs {
			data := ""
			if !r.GetBool("deleted") {
				data, _ = unseal(key, r.GetString("data"))
			}
			items = append(items, map[string]any{"group": gid, "uid": r.GetString("uid"), "data": data, "assignee": r.GetString("assignee"),
				"mode": r.GetString("mode"), "notify": r.GetBool("notify"), "deleted": r.GetBool("deleted"), "by": r.GetString("by"),
				"updated": int64(r.GetFloat("updated"))})
		}
		marks, _ := app.FindRecordsByFilter("group_done", "grp = {:g} && at >= {:s}", "at", 5000, 0, dbx.Params{"g": gid, "s": since - 5000})
		for _, d := range marks {
			done = append(done, map[string]any{"group": gid, "uid": d.GetString("uid"), "user": d.GetString("user"),
				"occurrence": int64(d.GetFloat("occurrence")), "undone": d.GetBool("undone"), "at": int64(d.GetFloat("at"))})
		}
		if since > 0 {
			news, _ := app.FindRecordsByFilter("group_events", "grp = {:g} && at > {:s} && actor != {:me}", "at", 200, 0,
				dbx.Params{"g": gid, "s": since, "me": me})
			for _, n := range news {
				if t := n.GetString("target"); t != "" && t != me {
					continue
				}
				text, _ := unseal(key, n.GetString("text"))
				actor, _ := app.FindRecordById("users", n.GetString("actor"))
				events = append(events, map[string]any{"group": gid, "kind": n.GetString("kind"), "actor": displayName(actor), "text": text,
					"at": int64(n.GetFloat("at"))})
			}
		}
	}
	return e.JSON(http.StatusOK, map[string]any{"now": now, "me": me, "groups": groups, "invites": invites, "items": items,
		"done": done, "events": events})
}

var lastCleanup time.Time

// cleanupGroups removes finished one-off reminders, deleted ones and news older than 30 days (at most hourly).
func cleanupGroups(app core.App) {
	if time.Since(lastCleanup) < time.Hour {
		return
	}
	lastCleanup = time.Now()
	old := time.Now().Add(-groupKeep).UnixMilli()
	for _, q := range []string{
		"DELETE FROM group_events WHERE at < {:old}",
		"DELETE FROM group_items WHERE at < {:old} AND (deleted = TRUE OR finished = TRUE)",
		"DELETE FROM group_done WHERE at < {:old} AND uid NOT IN (SELECT uid FROM group_items)",
	} {
		_, _ = app.DB().NewQuery(q).Bind(dbx.Params{"old": old}).Execute()
	}
}

func handleGroupCreate(e *core.RequestEvent) error {
	var body struct {
		Name     string `json:"name"`
		EditMode string `json:"edit_mode"`
	}
	if err := e.BindBody(&body); err != nil {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	name := strings.TrimSpace(body.Name)
	if name == "" || utf8.RuneCountInString(name) > 40 {
		return apis.NewBadRequestError("اسم گروه باید ۱ تا ۴۰ حرف باشد", nil)
	}
	if body.EditMode != "all" {
		body.EditMode = "owner"
	}
	if countActiveGroups(e.App, e.Auth.Id) >= groupMaxPerUser {
		return apis.NewBadRequestError("هر نفر حداکثر در ۵ گروه می‌تواند باشد", nil)
	}
	var view map[string]any
	err := e.App.RunInTransaction(func(tx core.App) error {
		gc, err := tx.FindCollectionByNameOrId("groups")
		if err != nil {
			return err
		}
		g := core.NewRecord(gc)
		g.Set("name", name)
		g.Set("owner", e.Auth.Id)
		g.Set("edit_mode", body.EditMode)
		if err := tx.Save(g); err != nil {
			return err
		}
		mc, err := tx.FindCollectionByNameOrId("group_members")
		if err != nil {
			return err
		}
		m := core.NewRecord(mc)
		m.Set("grp", g.Id)
		m.Set("user", e.Auth.Id)
		m.Set("role", "owner")
		m.Set("status", "active")
		if err := tx.Save(m); err != nil {
			return err
		}
		view = groupView(tx, g, e.Auth.Id)
		return nil
	})
	if err != nil {
		return err
	}
	return e.JSON(http.StatusOK, view)
}

// handleGroupSettings: the owner renames the group or changes who may edit.
func handleGroupSettings(e *core.RequestEvent) error {
	g, m, err := activeMember(e)
	if err != nil {
		return err
	}
	if m.GetString("role") != "owner" {
		return apis.NewForbiddenError("فقط سازندهٔ گروه می‌تواند", nil)
	}
	var body struct {
		Name     *string `json:"name"`
		EditMode *string `json:"edit_mode"`
	}
	if err := e.BindBody(&body); err != nil {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	if body.Name != nil {
		n := strings.TrimSpace(*body.Name)
		if n == "" || utf8.RuneCountInString(n) > 40 {
			return apis.NewBadRequestError("اسم گروه باید ۱ تا ۴۰ حرف باشد", nil)
		}
		g.Set("name", n)
	}
	if body.EditMode != nil && (*body.EditMode == "all" || *body.EditMode == "owner") {
		g.Set("edit_mode", *body.EditMode)
	}
	if err := e.App.Save(g); err != nil {
		return err
	}
	return e.JSON(http.StatusOK, groupView(e.App, g, e.Auth.Id))
}

func handleGroupInvite(e *core.RequestEvent) error {
	g, m, err := activeMember(e)
	if err != nil {
		return err
	}
	if g.GetString("edit_mode") != "all" && m.GetString("role") != "owner" {
		return apis.NewForbiddenError("در این گروه فقط سازنده می‌تواند عضو اضافه کند", nil)
	}
	var body struct {
		Username string `json:"username"`
	}
	if err := e.BindBody(&body); err != nil {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	u := strings.ToLower(strings.TrimPrefix(strings.TrimSpace(body.Username), "@"))
	target, _ := e.App.FindFirstRecordByFilter("users", "username = {:u}", dbx.Params{"u": u})
	if u == "" || target == nil {
		return apis.NewBadRequestError("کسی با این نام کاربری پیدا نشد", nil)
	}
	if membership(e.App, g.Id, target.Id) != nil {
		return apis.NewBadRequestError("این نفر از قبل در گروه است یا دعوت شده", nil)
	}
	if n, _ := e.App.CountRecords("group_members", dbx.NewExp("grp = {:g}", dbx.Params{"g": g.Id})); n >= groupMaxMembers {
		return apis.NewBadRequestError("هر گروه حداکثر ۲۰ نفر می‌تواند داشته باشد", nil)
	}
	dayAgo := time.Now().Add(-24 * time.Hour).UTC().Format("2006-01-02 15:04:05.000Z")
	if n, _ := e.App.CountRecords("group_members", dbx.NewExp("invited_by = {:u} AND created > {:d}", dbx.Params{"u": e.Auth.Id, "d": dayAgo})); n >= groupInvitesADay {
		return apis.NewBadRequestError("امروز دعوت زیادی فرستاده‌ای؛ فردا دوباره امتحان کن", nil)
	}
	mc, err := e.App.FindCollectionByNameOrId("group_members")
	if err != nil {
		return err
	}
	r := core.NewRecord(mc)
	r.Set("grp", g.Id)
	r.Set("user", target.Id)
	r.Set("role", "member")
	r.Set("status", "invited")
	r.Set("invited_by", e.Auth.Id)
	if err := e.App.Save(r); err != nil {
		return err
	}
	return e.JSON(http.StatusOK, groupView(e.App, g, e.Auth.Id))
}

// handleGroupAnswer accepts or declines an invitation.
func handleGroupAnswer(accept bool) func(*core.RequestEvent) error {
	return func(e *core.RequestEvent) error {
		m := membership(e.App, e.Request.PathValue("id"), e.Auth.Id)
		if m == nil || m.GetString("status") != "invited" {
			return apis.NewBadRequestError("دعوتی پیدا نشد", nil)
		}
		if !accept {
			if err := e.App.Delete(m); err != nil {
				return err
			}
			return e.JSON(http.StatusOK, map[string]any{"ok": true})
		}
		if countActiveGroups(e.App, e.Auth.Id) >= groupMaxPerUser {
			return apis.NewBadRequestError("هر نفر حداکثر در ۵ گروه می‌تواند باشد؛ اول از یکی خارج شو", nil)
		}
		m.Set("status", "active")
		if err := e.App.Save(m); err != nil {
			return err
		}
		me, _ := e.App.FindRecordById("users", e.Auth.Id)
		addEvent(e.App, m.GetString("grp"), "joined", e.Auth.Id, "", displayName(me))
		return e.JSON(http.StatusOK, map[string]any{"ok": true})
	}
}

// leaveGroup takes [userID] out; when the owner goes, the longest-standing member takes over, and a group left
// empty is deleted with everything in it.
func leaveGroup(app core.App, g *core.Record, userID string) error {
	m := membership(app, g.Id, userID)
	if m == nil {
		return nil
	}
	return app.RunInTransaction(func(tx core.App) error {
		if err := tx.Delete(m); err != nil {
			return err
		}
		if g.GetString("owner") != userID {
			return nil
		}
		next, _ := tx.FindFirstRecordByFilter("group_members", "grp = {:g} && status = 'active'", dbx.Params{"g": g.Id})
		if next == nil {
			return tx.Delete(g)
		}
		next.Set("role", "owner")
		if err := tx.Save(next); err != nil {
			return err
		}
		g.Set("owner", next.GetString("user"))
		return tx.Save(g)
	})
}

func handleGroupLeave(e *core.RequestEvent) error {
	g, _, err := activeMember(e)
	if err != nil {
		return err
	}
	me, _ := e.App.FindRecordById("users", e.Auth.Id)
	if err := leaveGroup(e.App, g, e.Auth.Id); err != nil {
		return err
	}
	addEvent(e.App, g.Id, "left", e.Auth.Id, "", displayName(me))
	return e.JSON(http.StatusOK, map[string]any{"ok": true})
}

func handleGroupRemove(e *core.RequestEvent) error {
	g, m, err := activeMember(e)
	if err != nil {
		return err
	}
	if m.GetString("role") != "owner" {
		return apis.NewForbiddenError("فقط سازندهٔ گروه می‌تواند کسی را حذف کند", nil)
	}
	var body struct {
		User string `json:"user"`
	}
	if err := e.BindBody(&body); err != nil || body.User == "" || body.User == e.Auth.Id {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	if err := leaveGroup(e.App, g, body.User); err != nil {
		return err
	}
	return e.JSON(http.StatusOK, groupView(e.App, g, e.Auth.Id))
}

func handleGroupDelete(e *core.RequestEvent) error {
	g, m, err := activeMember(e)
	if err != nil {
		return err
	}
	if m.GetString("role") != "owner" {
		return apis.NewForbiddenError("فقط سازندهٔ گروه می‌تواند گروه را پاک کند", nil)
	}
	if err := e.App.Delete(g); err != nil {
		return err
	}
	return e.JSON(http.StatusOK, map[string]any{"ok": true})
}

// handleGroupItem saves a reminder of the group (new, changed or deleted).
func handleGroupItem(e *core.RequestEvent) error {
	g, m, err := activeMember(e)
	if err != nil {
		return err
	}
	if g.GetString("edit_mode") != "all" && m.GetString("role") != "owner" {
		return apis.NewForbiddenError("در این گروه فقط سازنده می‌تواند یادآوری بسازد یا عوض کند", nil)
	}
	var body struct {
		UID      string `json:"uid"`
		Data     string `json:"data"`
		Assignee string `json:"assignee"`
		Mode     string `json:"mode"`
		Notify   bool   `json:"notify"`
		Deleted  bool   `json:"deleted"`
		Finished bool   `json:"finished"`
		Updated  int64  `json:"updated"`
	}
	if err := e.BindBody(&body); err != nil || body.UID == "" || len(body.UID) > 64 || len(body.Data) > 50000 {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	if body.Assignee != "" {
		if a := membership(e.App, g.Id, body.Assignee); a == nil || a.GetString("status") != "active" {
			return apis.NewBadRequestError("این نفر عضو گروه نیست", nil)
		}
	}
	if body.Mode != "all" {
		body.Mode = "one"
	}
	r, _ := e.App.FindFirstRecordByFilter("group_items", "grp = {:g} && uid = {:id}", dbx.Params{"g": g.Id, "id": body.UID})
	isNew := r == nil
	if isNew {
		if body.Deleted {
			return e.JSON(http.StatusOK, map[string]any{"ok": true})
		}
		col, err := e.App.FindCollectionByNameOrId("group_items")
		if err != nil {
			return err
		}
		if n, _ := e.App.CountRecords("group_items", dbx.NewExp("grp = {:g} AND deleted = FALSE", dbx.Params{"g": g.Id})); n >= 500 {
			return apis.NewBadRequestError("این گروه یادآوری زیادی دارد", nil)
		}
		r = core.NewRecord(col)
		r.Set("grp", g.Id)
		r.Set("uid", body.UID)
	}
	key := groupKey(e.App, g.Id)
	if body.Deleted {
		r.Set("data", "")
	} else {
		sealed, err := seal(key, body.Data)
		if err != nil {
			return err
		}
		r.Set("data", sealed)
	}
	r.Set("assignee", body.Assignee)
	r.Set("mode", body.Mode)
	r.Set("notify", body.Notify)
	r.Set("deleted", body.Deleted)
	r.Set("finished", body.Finished)
	r.Set("by", e.Auth.Id)
	r.Set("updated", body.Updated)
	r.Set("at", nowMs())
	if err := e.App.Save(r); err != nil {
		return err
	}
	if isNew {
		title := itemTitle(body.Data)
		addEvent(e.App, g.Id, "added", e.Auth.Id, body.Assignee, title)
	}
	return e.JSON(http.StatusOK, map[string]any{"ok": true})
}

func itemTitle(data string) string {
	var v struct {
		Title string `json:"title"`
	}
	_ = json.Unmarshal([]byte(data), &v)
	return v.Title
}

// handleGroupDone marks (or unmarks) that I did one occurrence of a group reminder.
func handleGroupDone(e *core.RequestEvent) error {
	g, _, err := activeMember(e)
	if err != nil {
		return err
	}
	var body struct {
		UID        string `json:"uid"`
		Occurrence int64  `json:"occurrence"`
		Undo       bool   `json:"undo"`
		Finished   bool   `json:"finished"`
	}
	if err := e.BindBody(&body); err != nil || body.UID == "" {
		return apis.NewBadRequestError("درخواست نامعتبر است", nil)
	}
	item, _ := e.App.FindFirstRecordByFilter("group_items", "grp = {:g} && uid = {:id}", dbx.Params{"g": g.Id, "id": body.UID})
	if item == nil {
		return apis.NewNotFoundError("این یادآوری دیگر در گروه نیست", nil)
	}
	d, _ := e.App.FindFirstRecordByFilter("group_done", "grp = {:g} && uid = {:id} && user = {:u} && occurrence = {:o}",
		dbx.Params{"g": g.Id, "id": body.UID, "u": e.Auth.Id, "o": body.Occurrence})
	if d == nil {
		col, err := e.App.FindCollectionByNameOrId("group_done")
		if err != nil {
			return err
		}
		d = core.NewRecord(col)
		d.Set("grp", g.Id)
		d.Set("uid", body.UID)
		d.Set("user", e.Auth.Id)
		d.Set("occurrence", body.Occurrence)
	}
	d.Set("undone", body.Undo)
	d.Set("at", nowMs())
	if err := e.App.Save(d); err != nil {
		return err
	}
	if body.Finished && !body.Undo {
		item.Set("finished", true)
		item.Set("at", nowMs())
		_ = e.App.Save(item)
	}
	if !body.Undo && item.GetBool("notify") {
		data, _ := unseal(groupKey(e.App, g.Id), item.GetString("data"))
		addEvent(e.App, g.Id, "done", e.Auth.Id, "", itemTitle(data))
	}
	return e.JSON(http.StatusOK, map[string]any{"ok": true})
}
