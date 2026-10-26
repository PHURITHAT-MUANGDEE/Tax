"""ทดสอบหน้าเว็บ (static/index.html) กับ "หลังบ้านจำลอง" ที่เขียนตามสัญญา API ของ Spring Boot
ไม่ได้รัน Spring จริง จึงพิสูจน์ได้เฉพาะว่าหน้าเว็บเรียก /api ถูกรูปแบบ (เมธอด โครงสร้าง JSON โทเคน CSRF การจัดการ 401)
ใช้ Playwright (Python): python3 tests/e2e.py
"""
import sys, json, re, threading, functools, http.server, pathlib
from playwright.sync_api import sync_playwright

STATIC = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/static"
SHOT = pathlib.Path("/tmp/claude-0/-home-claude/cbb3dd6f-ff36-51a7-89f5-80c65f4d777d/scratchpad")
PORT = 8765
BASE = f"http://localhost:{PORT}"
XSRF = "test-xsrf-token"
ok = True
def check(name, cond, extra=""):
    global ok
    print(("PASS " if cond else "FAIL ") + name + (" | " + str(extra) if extra and not cond else ""))
    ok = ok and bool(cond)

# ---------- หลังบ้านจำลอง ----------
UUID_RE = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", re.I)
class Backend:
    def __init__(self):
        self.logged_in = False; self.deleted = False
        self.profile = {}; self.prefs = {}; self.entries = {}; self.next_id = 1; self.years = set(); self.legacy = None
        self.settings = {"email": "tester@example.com", "enabled": False, "send_minute": 540, "monthly": True,
                         "monthly_when": ["custom"], "remind_day": 25, "deadline": True, "deadline_days": [14, 7, 1],
                         "yearend": True, "consent_at": None, "unsubscribed_at": None}
        self.unsub_token = "22222222-2222-2222-2222-222222222222"
        self.tos_current = "T1"; self.tos_version = None
        self.test_mail_calls = 0; self.test_mail_mode = "ok"
        self.csrf_missing = 0; self.calls = []; self.expire_next_put = False

    @property
    def state(self):
        yrs = {str(y): {"months": {}} for y in self.years}
        for e in self.entries.values():
            mo = yrs.setdefault(str(e["yearBe"]), {"months": {}})["months"]
            mo.setdefault(str(e["month"]), {}).setdefault(e["category"], []).append(
                {"id": e["id"], "i": e["amount"], "w": e["withheld"], "t": e["method"], "n": e["name"]})
        return {"profile": self.profile, "wm": self.prefs, "years": yrs}

    def entry_ok(self, b, create):
        keys = {"yearBe", "month", "category", "amount", "withheld", "method", "name"}
        if set(b) - keys: return False
        if create and not {"yearBe", "month", "category"} <= set(b): return False
        if "category" in b and b["category"] not in {"c%d" % i for i in range(1, 9)}: return False
        if "month" in b and not (isinstance(b["month"], int) and 1 <= b["month"] <= 12): return False
        if "yearBe" in b and not (isinstance(b["yearBe"], int) and 2500 <= b["yearBe"] <= 2700): return False
        for k in ("amount", "withheld"):
            if k in b and not (isinstance(b[k], (int, float)) and b[k] >= 0): return False
        return True

    def patch_settings(self, p):
        allowed = {"enabled", "monthly", "deadline", "yearend", "send_minute", "remind_day", "monthly_when", "deadline_days"}
        if not p or set(p) - allowed: return 400, {"error": "bad"}
        if "send_minute" in p and not (isinstance(p["send_minute"], int) and 0 <= p["send_minute"] <= 1439): return 400, {"error": "bad"}
        if "remind_day" in p and not (isinstance(p["remind_day"], int) and 1 <= p["remind_day"] <= 28): return 400, {"error": "bad"}
        if "monthly_when" in p and not set(p["monthly_when"]) <= {"start", "end", "custom"}: return 400, {"error": "bad"}
        if "deadline_days" in p and not set(p["deadline_days"]) <= {0, 1, 3, 7, 14, 30}: return 400, {"error": "bad"}
        if p.get("enabled") and not self.settings["enabled"]:
            self.settings["consent_at"] = "2026-10-20T09:00:00Z"; self.settings["unsubscribed_at"] = None
        self.settings.update(p)
        return 200, self.settings

    def handle(self, route):
        req = route.request; path = re.sub(r"^https?://[^/]+", "", req.url).split("?")[0]; m = req.method
        self.calls.append((m, path))
        def send(status, body=None):
            if status == 204: route.fulfill(status=204)
            else: route.fulfill(status=status, body=json.dumps(body if body is not None else {}), content_type="application/json")
        if path == "/oauth2/authorization/google":
            self.logged_in = True; self.deleted = False
            return route.fulfill(status=302, headers={"Location": "/"})
        if path == "/api/unsubscribe":
            if m != "POST": return send(405)
            tok = (json.loads(req.post_data or "{}").get("token") if req.post_data else None)
            if not tok or not UUID_RE.match(tok): return send(400, {"error": "invalid_token"})
            return send(200, {"ok": True}) if tok == self.unsub_token else send(404, {"error": "not_found"})
        # ต่อจากนี้ต้องล็อกอิน
        if not self.logged_in: return send(401)
        if m != "GET" and req.headers.get("x-xsrf-token") != XSRF:
            self.csrf_missing += 1; return send(403, {"error": "csrf"})
        if path == "/logout" and m == "POST": self.logged_in = False; return send(204)
        if path == "/api/me": return send(200, {"email": "tester@example.com", "tos_current": self.tos_current, "tos_version": self.tos_version})
        if path == "/api/consent/tos" and m == "POST":
            if json.loads(req.post_data or "{}").get("version") != self.tos_current: return send(409)
            self.tos_version = self.tos_current; return send(204)
        if path == "/api/workbook" and m == "GET":
            if self.legacy is not None: return send(200, self.legacy)
            return send(200, self.state)
        if path == "/api/profile" and m == "PUT":
            if self.expire_next_put: self.expire_next_put = False; self.logged_in = False; return send(401)
            self.profile = json.loads(req.post_data); return send(200, self.profile)
        mp = re.match(r"^/api/withhold-prefs/(c[1-8])$", path)
        if mp and m == "PUT": self.prefs[mp.group(1)] = json.loads(req.post_data)["method"]; return send(200, {"category": mp.group(1)})
        if mp and m == "DELETE":
            return send(204) if self.prefs.pop(mp.group(1), None) is not None else send(404)
        if path == "/api/entries" and m == "POST":
            if self.expire_next_put: self.expire_next_put = False; self.logged_in = False; return send(401)
            b = json.loads(req.post_data)
            if not self.entry_ok(b, True): return send(400, {"error": "invalid"})
            e = dict(b); e["id"] = self.next_id; self.next_id += 1
            self.entries[e["id"]] = e; self.years.add(e["yearBe"]); return send(201, e)
        me = re.match(r"^/api/entries/(\d+)$", path)
        if me:
            eid = int(me.group(1))
            if eid not in self.entries: return send(404, {"error": "not_found"})
            if m == "PUT":
                b = json.loads(req.post_data)
                if not self.entry_ok(b, False): return send(400, {"error": "invalid"})
                self.entries[eid].update(b); self.years.add(self.entries[eid]["yearBe"]); return send(200, self.entries[eid])
            if m == "DELETE": del self.entries[eid]; return send(204)
        if path == "/api/settings" and m == "GET": return send(200, self.settings)
        if path == "/api/settings" and m == "PATCH":
            st, body = self.patch_settings(json.loads(req.post_data)); return send(st, body)
        if path == "/api/notifications/test" and m == "POST":
            self.test_mail_calls += 1
            if self.test_mail_mode == "ok": return send(204)
            return send({"limit": 429, "nomail": 503}.get(self.test_mail_mode, 502), {"error": self.test_mail_mode})
        if path == "/api/account" and m == "DELETE": self.logged_in = False; self.deleted = True; return send(204)
        return send(404)

def serve():
    h = functools.partial(http.server.SimpleHTTPRequestHandler, directory=str(STATIC))
    h.log_message = lambda *a, **k: None
    class Q(http.server.SimpleHTTPRequestHandler):
        def log_message(self, *a): pass
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", PORT), functools.partial(Q, directory=str(STATIC)))
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    return srv

srv = serve()
B = Backend()
with sync_playwright() as p:
    b = p.chromium.launch()
    ctx = b.new_context(viewport={"width": 1200, "height": 1000}, accept_downloads=True)
    ctx.add_cookies([{"name": "XSRF-TOKEN", "value": XSRF, "url": BASE}])
    pg = ctx.new_page()
    errs = []; pg.on("pageerror", lambda e: errs.append(str(e)))
    api_re = re.compile(r"^http://localhost:%d/(api/.*|logout|oauth2/.*)$" % PORT)
    pg.route(api_re, B.handle)
    pg.route("**/fonts.googleapis.com/**", lambda r: r.abort())

    pg.goto(BASE + "/"); pg.wait_for_timeout(500)
    check("gate shown when signed out (401 from /api/me)", pg.is_visible("#gate") and not pg.is_visible("#app"))
    check("no scripts load from supabase", "supabase" not in pg.content().lower())
    check("login button disabled until TOS ticked", pg.is_disabled("#gateBtn"))
    check("gate links to privacy and terms", pg.locator("#gate .tosrow a[href='privacy.html']").count() == 1 and pg.locator("#gate .tosrow a[href='terms.html']").count() == 1)
    pg.check("#gateTos"); check("tick enables login button", pg.is_enabled("#gateBtn"))
    pg.click("#gateBtn"); pg.wait_for_timeout(800)
    check("first login auto-records TOS acceptance", ("POST", "/api/consent/tos") in B.calls and B.tos_version == "T1", B.calls)
    check("login redirect returns to app", pg.is_visible("#app") and not pg.is_visible("#gate"), pg.url)
    check("side user email", pg.inner_text("#side-user") == "tester@example.com")
    check("new user starts blank (no sample)", pg.inner_text("#whBig") == "฿0")

    pg.evaluate("location.hash='#monthly'"); pg.wait_for_timeout(200)
    pg.click("#in-c1-0"); pg.keyboard.type("50000", delay=20)
    pg.click("[data-add=c4]"); pg.wait_for_timeout(100)
    pg.click("#in-c4-0"); pg.keyboard.type("1200", delay=20)
    pg.click("#in-c4-1"); pg.keyboard.type("2000", delay=20)
    pg.select_option("#ws-c4-1", "10")
    pg.wait_for_timeout(1500)
    st = B.state
    mon = pg.evaluate("new Date().getMonth()+1"); ny = pg.evaluate("new Date().getFullYear()+543")
    mo = st.get("years", {}).get(str(ny), {}).get("months", {}).get(str(mon), {})
    c1 = (mo.get("c1") or [{}])[0]; c4 = mo.get("c4") or []
    check("entries sent one by one via POST /api/entries", ("POST", "/api/entries") in B.calls and ("PUT", "/api/state") not in B.calls)
    check("unchanged profile is not re-sent", ("PUT", "/api/profile") not in B.calls)
    pg.evaluate("location.hash='#profile'"); pg.wait_for_timeout(200)
    pg.fill("#p-nick", "ต้น"); pg.wait_for_timeout(1500)
    check("profile edit sent via PUT /api/profile", B.profile.get("nick") == "ต้น" and "project" in B.profile and "sample" in B.profile, B.profile)
    pg.evaluate("location.hash='#monthly'"); pg.wait_for_timeout(200)
    check("entries saved as separate rows with ids", len(B.entries) == 3 and all("id" in e for e in B.entries.values()), B.entries)
    check("income saved to server state", c1.get("i") == 50000, st)
    check("two 40(4) entries saved separately", [e.get("i") for e in c4] == [1200, 2000] and c4[1].get("t") == "10", c4)
    pg.wait_for_timeout(800); check("status says saved", "บันทึกแล้ว" in pg.inner_text(".savestat"), pg.inner_text(".savestat"))
    check("every mutating call carried the CSRF header", B.csrf_missing == 0, B.csrf_missing)

    pg.evaluate("location.hash='#notify'"); pg.wait_for_timeout(200)
    check("notify tab is in the menu and not part of profile", pg.is_visible("a[data-nav=notify]") and not pg.query_selector("#page-profile #n-enabled") and pg.query_selector("#page-notify #n-enabled"))
    check("notify page visible, profile hidden", pg.is_visible("#page-notify") and not pg.is_visible("#page-profile"))
    check("notification off by default", not pg.is_checked("#n-enabled") and pg.is_disabled("#n-monthly"))
    pg.check("#n-enabled"); pg.wait_for_timeout(300)
    check("consent recorded", B.settings["enabled"] is True and B.settings["consent_at"], B.settings)
    check("options enabled after consent", not pg.is_disabled("#n-monthly"))
    pg.select_option("#n-day", "20"); pg.wait_for_timeout(300)
    pg.uncheck("#n-yearend"); pg.wait_for_timeout(300)
    check("day and toggles saved", B.settings["remind_day"] == 20 and B.settings["yearend"] is False and B.settings["deadline"] is True, B.settings)
    check("defaults shown", pg.input_value("#n-time") == "540" and pg.is_checked("#n-when-custom") and not pg.is_checked("#n-when-start")
          and pg.is_checked("#n-dd-14") and pg.is_checked("#n-dd-7") and pg.is_checked("#n-dd-1") and not pg.is_checked("#n-dd-30"))
    pg.screenshot(path=str(SHOT / "spring-notify.png"), full_page=True)
    pg.click("#n-test"); pg.wait_for_timeout(300)
    check("test email button calls API and confirms", B.test_mail_calls == 1 and "ส่งแล้ว" in pg.inner_text("#n-test-msg") and "tester@example.com" in pg.inner_text("#n-test-msg"), pg.inner_text("#n-test-msg"))
    for mode, text in [("limit", "ส่งถี่เกินไป"), ("nomail", "ยังไม่ได้ตั้งค่าอีเมลผู้ส่ง"), ("fail", "ส่งไม่สำเร็จ")]:
        B.test_mail_mode = mode; pg.click("#n-test"); pg.wait_for_timeout(250)
        check("test email error message: " + mode, text in pg.inner_text("#n-test-msg"), pg.inner_text("#n-test-msg"))
    B.test_mail_mode = "ok"
    pg.select_option("#n-time", "1170"); pg.wait_for_timeout(300)
    pg.check("#n-when-start"); pg.check("#n-when-end"); pg.wait_for_timeout(200)
    pg.uncheck("#n-when-custom"); pg.wait_for_timeout(300)
    check("day select disabled without custom", pg.is_disabled("#n-day"))
    pg.check("#n-dd-30"); pg.check("#n-dd-0"); pg.uncheck("#n-dd-14"); pg.wait_for_timeout(300)
    check("schedule saved", B.settings["send_minute"] == 1170 and sorted(B.settings["monthly_when"]) == ["end", "start"]
          and B.settings["deadline_days"] == [30, 7, 1, 0], B.settings)
    pg.uncheck("#n-when-start"); pg.uncheck("#n-when-end"); pg.wait_for_timeout(300)
    check("warns when no monthly timing chosen", "ยังไม่ได้เลือกช่วงเตือน" in pg.inner_text("#n-state"))
    pg.check("#n-when-custom"); pg.wait_for_timeout(300)
    check("saved note shown", "บันทึกการตั้งค่าแล้ว" in pg.inner_text("#n-state"))

    pg.evaluate("location.hash='#profile'"); pg.wait_for_timeout(200)
    with pg.expect_download() as dl:
        pg.click("#exportBtn")
    exp = json.loads(open(dl.value.path(), encoding="utf8").read())
    check("export has state and no unsub token", exp["state"]["years"] and "unsub_token" not in json.dumps(exp), list(exp.keys()))

    pg.reload(); pg.wait_for_timeout(700)
    check("session restored on reload, no gate", pg.is_visible("#app"))
    # ข้อกำหนดเปลี่ยนเวอร์ชัน: ต้องติ๊กรับใหม่ก่อนเข้าแอป
    B.tos_current = "T2"; pg.reload(); pg.wait_for_timeout(600)
    check("new TOS version blocks app until accepted", pg.is_visible("#tosGate") and not pg.is_visible("#app") and pg.is_disabled("#tosBtn"))
    pg.check("#tosTick"); pg.click("#tosBtn"); pg.wait_for_timeout(600)
    check("accepting new version enters app and records it", pg.is_visible("#app") and B.tos_version == "T2")
    pg.evaluate("location.hash='#monthly'"); pg.wait_for_timeout(200)
    check("data loaded back from server", pg.input_value("#in-c1-0") == "50000" and pg.input_value("#in-c4-1") == "2000")
    pg.evaluate("location.hash='#notify'"); pg.wait_for_timeout(200)
    check("settings loaded back", pg.is_checked("#n-enabled") and pg.input_value("#n-day") == "20" and pg.input_value("#n-time") == "1170"
          and pg.is_checked("#n-dd-30") and pg.is_checked("#n-dd-0") and not pg.is_checked("#n-dd-14"))

    # ---------- ระบบเปลี่ยนปี + ประวัติรายได้ ----------
    pg.evaluate("location.hash='#monthly'"); pg.wait_for_timeout(200)
    ly = ny - 1
    check("year select defaults to current year", pg.input_value("#yearSel") == str(ny))
    pg.select_option("#yearSel", str(ly)); pg.wait_for_timeout(300)
    check("switching year shows empty months", pg.input_value("#in-c1-0") == "" and str(ly) in pg.inner_text("#monTitle"))
    pg.click("#in-c1-0"); pg.keyboard.type("30000", delay=20)
    pg.click("[data-add=c2]"); pg.click("#in-c2-0"); pg.keyboard.type("5000", delay=20)
    pg.wait_for_timeout(1500)
    mo = B.state["years"].get(str(ly), {}).get("months", {})
    check("previous year saved separately", any((m.get("c1") or [{}])[0].get("i") == 30000 for m in mo.values()), B.state)
    check("current year untouched", B.state["years"][str(ny)]["months"][str(mon)]["c1"][0]["i"] == 50000)
    pg.select_option("#yearSel", str(ny)); pg.wait_for_timeout(300)
    check("back to current year keeps data", pg.input_value("#in-c1-0") == "50000")
    pg.evaluate("location.hash='#summary'"); pg.wait_for_timeout(300)
    check("home history defaults to overall", pg.input_value("#logSel") == "all")
    tbl = pg.inner_text("#logBody")
    check("overall lists both years and a total", str(ny) in tbl and str(ly) in tbl and "รวมทุกปี" in tbl, tbl[:200])
    pg.screenshot(path=str(SHOT / "spring-home.png"), full_page=True)
    pg.select_option("#logSel", str(ly)); pg.wait_for_timeout(200)
    tbl = pg.inner_text("#logBody")
    check("single-year log shows that year's entries only", "30,000" in tbl and "5,000" in tbl and "50,000" not in tbl and "40(2)" in tbl, tbl[:300])
    pg.select_option("#logSel", "all"); pg.wait_for_timeout(100)
    pg.click("#logBody a[data-logyear='%d']" % ly); pg.wait_for_timeout(200)
    check("click year link opens that year's log", pg.input_value("#logSel") == str(ly))
    pg.click("#logBody a[data-gom]"); pg.wait_for_timeout(400)
    check("click month jumps to monthly of that year", pg.input_value("#yearSel") == str(ly) and pg.input_value("#in-c1-0") == "30000" and pg.is_visible("#page-monthly"))
    pg.select_option("#yearSel", str(ny)); pg.evaluate("location.hash='#summary'"); pg.wait_for_timeout(300)
    check("projection toggle only for current year", pg.is_visible("#vtoggle"))
    pg.select_option("#yearSel", str(ly)); pg.wait_for_timeout(300)
    check("projection toggle hidden for past year", not pg.is_visible("#vtoggle") and "ปีที่ผ่านมา" in pg.inner_text("#sumEyebrow"))
    pg.select_option("#yearSel", str(ny)); pg.wait_for_timeout(200)

    # รุ่นเก่า (ยังไม่แยกปี) ต้องย้ายเข้าปี 2569
    B.legacy = {"v": 1, "months": {"3": {"c1": [{"i": 42000, "w": 0, "t": "none", "n": "เก่า"}]}}, "profile": {}}
    pg.reload(); pg.wait_for_timeout(700); B.legacy = None
    pg.select_option("#yearSel", "2569"); pg.evaluate("location.hash='#monthly'"); pg.wait_for_timeout(300)
    pg.click(".chip[data-m='3']"); pg.wait_for_timeout(200)
    check("legacy months migrate into 2569", pg.input_value("#in-c1-0") == "42000")

    # ---------- เซสชันหมดอายุระหว่างบันทึก ----------
    B.expire_next_put = True
    pg.click("#in-c1-0"); pg.keyboard.type("1", delay=20); pg.wait_for_timeout(1500)
    check("expired session during save returns to gate with message", pg.is_visible("#gate") and "หมดอายุ" in pg.inner_text("#gateMsg"), pg.inner_text("#gateMsg"))
    pg.check("#gateTos"); pg.click("#gateBtn"); pg.wait_for_timeout(800)
    check("can sign in again after expiry", pg.is_visible("#app"))

    pg.set_viewport_size({"width": 390, "height": 800}); pg.wait_for_timeout(300)
    pg.evaluate("location.hash='#summary'"); pg.wait_for_timeout(300)
    check("no horizontal scroll on mobile (home)", not pg.evaluate("document.documentElement.scrollWidth > innerWidth"))
    pg.evaluate("location.hash='#notify'"); pg.wait_for_timeout(200)
    check("no horizontal scroll on mobile (notify)", not pg.evaluate("document.documentElement.scrollWidth > innerWidth"))
    check("mobile bottom nav fits 6 items", pg.evaluate("[...document.querySelectorAll('.navitem')].every(a=>{const r=a.getBoundingClientRect();return r.left>=0&&r.right<=innerWidth+1})"))
    pg.screenshot(path=str(SHOT / "spring-notify-mobile.png"), full_page=False)
    pg.evaluate("location.hash='#profile'"); pg.wait_for_timeout(200)
    check("no horizontal scroll on mobile (profile)", not pg.evaluate("document.documentElement.scrollWidth > innerWidth"))
    pg.set_viewport_size({"width": 1200, "height": 1000}); pg.wait_for_timeout(200)

    pg.click("#signOutBtn"); pg.wait_for_timeout(500)
    check("sign out calls POST /logout and returns to gate", ("POST", "/logout") in B.calls and pg.is_visible("#gate") and not pg.is_visible("#app"))
    pg.reload(); pg.wait_for_timeout(500)
    check("after reload still signed out", pg.is_visible("#gate"))
    pg.check("#gateTos"); pg.click("#gateBtn"); pg.wait_for_timeout(800)
    pg.evaluate("location.hash='#profile'"); pg.wait_for_timeout(200)
    pg.click("#deleteBtn")
    check("delete needs second click", pg.is_visible("#app") and not B.deleted)
    pg.click("#deleteBtn"); pg.wait_for_timeout(1000)
    check("DELETE /api/account sent and back at gate", B.deleted and ("DELETE", "/api/account") in B.calls and pg.is_visible("#gate"), B.calls[-4:])
    check("CSRF header present on logout/delete too", B.csrf_missing == 0, B.csrf_missing)

    # ---------- หน้ายกเลิกรับอีเมล ----------
    up = ctx.new_page(); reqs = []
    up.route("**/fonts.googleapis.com/**", lambda r: r.abort())
    def on_unsub(route):
        reqs.append((route.request.method, route.request.post_data)); B.handle(route)
    up.route(re.compile(r"^http://localhost:%d/api/unsubscribe$" % PORT), on_unsub)
    up.goto(BASE + "/unsubscribe.html?t=22222222-2222-2222-2222-222222222222"); up.wait_for_timeout(200)
    up.click("#btn"); up.wait_for_timeout(400)
    check("unsubscribe page POSTs token to /api/unsubscribe", reqs and reqs[0][0] == "POST" and json.loads(reqs[0][1])["token"] == "22222222-2222-2222-2222-222222222222", reqs)
    check("unsubscribe success message", "ยกเลิกเรียบร้อย" in up.inner_text("#msg"))
    up.goto(BASE + "/unsubscribe.html?t=bad"); up.wait_for_timeout(200)
    check("bad token disables button", up.is_disabled("#btn"))
    up.goto(BASE + "/unsubscribe.html?t=33333333-3333-3333-3333-333333333333"); up.wait_for_timeout(200)
    up.click("#btn"); up.wait_for_timeout(400)
    check("unknown token shows not-found message", "ไม่พบรายการนี้" in up.inner_text("#msg"))

    check("no page errors", not errs, errs)
    b.close()
srv.shutdown()
print("ALL PASS" if ok else "SOME FAILED")
sys.exit(0 if ok else 1)
