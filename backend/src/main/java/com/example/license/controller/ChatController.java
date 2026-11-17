package com.example.license.controller;

import com.example.license.config.AdminAuth;
import com.example.license.entity.ChatMessage;
import com.example.license.repository.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Support chat.
 *
 *  GET  /chat/{appId}               the page the SDK opens in its chat sheet (WebView)
 *  POST /api/chat/send              visitor sends a message (public; identified by random sessionId)
 *  GET  /api/chat/messages          visitor polls for new messages
 *  GET  /api/chat/conversations     (admin) list sessions of an app
 *  GET  /api/chat/conversation      (admin) full thread of one session
 *  POST /api/chat/reply             (admin) agent answers
 *
 * Visitors are anonymous: the chat page creates a random session id and keeps
 * it in the WebView's localStorage. Text is always rendered with textContent
 * (never as HTML) and length-limited.
 */
@RestController
public class ChatController {

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");
    private static final int MAX_TEXT = 2000;

    @Autowired
    private ChatMessageRepository repository;
    @Autowired
    private AdminAuth adminAuth;

    // ----------------------------------------------------------------- page

    @GetMapping(value = "/chat/{appId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(@PathVariable String appId) {
        if (!SAFE_ID.matcher(appId).matches()) {
            return ResponseEntity.badRequest().body("Invalid app id");
        }
        // appId is validated against SAFE_ID, so embedding it as a JS string is safe.
        return ResponseEntity.ok(CHAT_HTML.replace("__APP_ID__", appId));
    }

    // -------------------------------------------------------------- visitor

    @PostMapping("/api/chat/send")
    public ResponseEntity<Map<String, Object>> send(@RequestBody Map<String, Object> body) {
        String appId = id(body.get("appId"));
        String sessionId = id(body.get("sessionId"));
        String text = body.get("text") == null ? null : String.valueOf(body.get("text")).trim();
        if (appId == null || sessionId == null || text == null || text.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "appId, sessionId and text are required"));
        }
        if (text.length() > MAX_TEXT) {
            text = text.substring(0, MAX_TEXT);
        }
        ChatMessage m = new ChatMessage();
        m.setAppId(appId);
        m.setSessionId(sessionId);
        m.setSender("user");
        m.setSenderName("You");
        m.setText(text);
        return ResponseEntity.ok(toJson(repository.save(m)));
    }

    @GetMapping("/api/chat/messages")
    public ResponseEntity<List<Map<String, Object>>> messages(
            @RequestParam String appId,
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "0") Long after) {
        if (id(appId) == null || id(sessionId) == null) {
            return ResponseEntity.badRequest().build();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (ChatMessage m : repository.findByAppIdAndSessionIdAndIdGreaterThanOrderByIdAsc(
                appId, sessionId, Math.max(0L, after))) {
            out.add(toJson(m));
        }
        return ResponseEntity.ok(out);
    }

    // ---------------------------------------------------------------- admin

    @GetMapping("/api/chat/conversations")
    public ResponseEntity<?> conversations(
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
            @RequestParam String appId) {
        ResponseEntity<?> denied = checkAdmin(adminToken);
        if (denied != null) {
            return denied;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (String sessionId : repository.findSessionIds(appId)) {
            repository.findFirstByAppIdAndSessionIdOrderByIdDesc(appId, sessionId).ifPresent(last -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("sessionId", sessionId);
                item.put("lastMessage", last.getText());
                item.put("lastSender", last.getSender());
                item.put("lastAt", String.valueOf(last.getCreatedAt()));
                out.add(item);
            });
        }
        return ResponseEntity.ok(out);
    }

    @GetMapping("/api/chat/conversation")
    public ResponseEntity<?> conversation(
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
            @RequestParam String appId,
            @RequestParam String sessionId) {
        ResponseEntity<?> denied = checkAdmin(adminToken);
        if (denied != null) {
            return denied;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (ChatMessage m : repository.findByAppIdAndSessionIdAndIdGreaterThanOrderByIdAsc(appId, sessionId, 0L)) {
            out.add(toJson(m));
        }
        return ResponseEntity.ok(out);
    }

    @PostMapping("/api/chat/reply")
    public ResponseEntity<?> reply(
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
            @RequestBody Map<String, Object> body) {
        ResponseEntity<?> denied = checkAdmin(adminToken);
        if (denied != null) {
            return denied;
        }
        String appId = id(body.get("appId"));
        String sessionId = id(body.get("sessionId"));
        String text = body.get("text") == null ? null : String.valueOf(body.get("text")).trim();
        if (appId == null || sessionId == null || text == null || text.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "appId, sessionId and text are required"));
        }
        ChatMessage m = new ChatMessage();
        m.setAppId(appId);
        m.setSessionId(sessionId);
        m.setSender("agent");
        m.setSenderName(body.get("agentName") == null ? "Support" : String.valueOf(body.get("agentName")));
        m.setText(text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) : text);
        return ResponseEntity.ok(toJson(repository.save(m)));
    }

    // -------------------------------------------------------------- helpers

    private ResponseEntity<?> checkAdmin(String adminToken) {
        if (!adminAuth.isConfigured()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Admin API disabled: set plumb5.admin.token"));
        }
        if (!adminAuth.isAuthorized(adminToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid admin token"));
        }
        return null;
    }

    private static String id(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return SAFE_ID.matcher(s).matches() ? s : null;
    }

    private static Map<String, Object> toJson(ChatMessage m) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", m.getId());
        item.put("sender", m.getSender());
        item.put("senderName", m.getSenderName());
        item.put("text", m.getText());
        item.put("createdAt", String.valueOf(m.getCreatedAt()));
        return item;
    }

    private static final String CHAT_HTML = """
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Chat</title>
            <style>
              html,body{height:100%;margin:0;font-family:-apple-system,Roboto,Arial,sans-serif;background:#f4f5f7}
              #wrap{display:flex;flex-direction:column;height:100%}
              #log{flex:1;overflow-y:auto;padding:12px}
              .m{max-width:78%;margin:6px 0;padding:8px 12px;border-radius:14px;line-height:1.35;word-wrap:break-word;white-space:pre-wrap}
              .user{background:#1976d2;color:#fff;margin-left:auto;border-bottom-right-radius:4px}
              .agent{background:#fff;color:#222;border-bottom-left-radius:4px;box-shadow:0 1px 2px rgba(0,0,0,.15)}
              .who{font-size:11px;opacity:.7;margin-bottom:2px}
              #bar{display:flex;gap:8px;padding:8px;background:#fff;border-top:1px solid #ddd}
              #t{flex:1;padding:10px;border:1px solid #ccc;border-radius:20px;font-size:16px}
              #b{padding:0 18px;border:0;border-radius:20px;background:#1976d2;color:#fff;font-size:16px}
              #empty{color:#888;text-align:center;margin-top:30px}
            </style>
            </head>
            <body>
            <div id="wrap">
              <div id="log"><div id="empty">Send us a message and we will reply here.</div></div>
              <form id="bar"><input id="t" autocomplete="off" maxlength="2000" placeholder="Type a message"><button id="b" type="submit">Send</button></form>
            </div>
            <script>
            (function(){
              var APP="__APP_ID__", KEY="p5_chat_session_"+APP, last=0, sid=null;
              try{ sid=localStorage.getItem(KEY); }catch(e){}
              if(!sid){
                sid=(window.crypto&&crypto.randomUUID)?crypto.randomUUID():("s"+Date.now()+Math.random().toString(16).slice(2));
                try{ localStorage.setItem(KEY,sid); }catch(e){}
              }
              var log=document.getElementById("log"), input=document.getElementById("t");
              function add(m){
                var e=document.getElementById("empty"); if(e) e.remove();
                var d=document.createElement("div"); d.className="m "+(m.sender==="user"?"user":"agent");
                if(m.sender!=="user"){ var w=document.createElement("div"); w.className="who"; w.textContent=m.senderName||"Support"; d.appendChild(w); }
                var s=document.createElement("div"); s.textContent=m.text; d.appendChild(s);
                log.appendChild(d); log.scrollTop=log.scrollHeight;
                if(m.id>last) last=m.id;
              }
              function poll(){
                fetch("/api/chat/messages?appId="+encodeURIComponent(APP)+"&sessionId="+encodeURIComponent(sid)+"&after="+last)
                  .then(function(r){return r.ok?r.json():[];}).then(function(a){a.forEach(add);}).catch(function(){});
              }
              document.getElementById("bar").addEventListener("submit",function(ev){
                ev.preventDefault();
                var text=input.value.trim(); if(!text) return; input.value="";
                fetch("/api/chat/send",{method:"POST",headers:{"Content-Type":"application/json"},
                  body:JSON.stringify({appId:APP,sessionId:sid,text:text})})
                  .then(function(r){return r.ok?r.json():null;}).then(function(m){ if(m) add(m); }).catch(function(){});
              });
              poll(); setInterval(poll,3000);
            })();
            </script>
            </body>
            </html>
            """;
}
