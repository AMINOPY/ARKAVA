package xray

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestStartUsesVersionedRunRequest(t *testing.T) {
	called := false
	b, err := NewBridge(func(raw string) string {
		called = true
		var req struct { APIVersion int `json:"apiVersion"`; Method string `json:"method"`; Payload struct { XrayJSON string `json:"xrayJson"` } `json:"payload"` }
		if err := json.Unmarshal([]byte(raw), &req); err != nil { t.Fatalf("bad request JSON: %v", err) }
		if req.APIVersion != 3 || req.Method != "runXray" || req.Payload.XrayJSON != `{"outbounds":[{"protocol":"freedom"}]}` { t.Fatalf("unexpected request: %+v", req) }
		return `{"success":true,"data":{},"error":""}`
	})
	if err != nil { t.Fatal(err) }
	if err := b.Start(`{"outbounds":[{"protocol":"freedom"}]}`); err != nil { t.Fatal(err) }
	if !called { t.Fatal("invoker was not called") }
}

func TestRejectsInvalidConfigBeforeInvoke(t *testing.T) {
	called := false
	b, _ := NewBridge(func(string) string { called = true; return `{"success":true,"data":{},"error":""}` })
	if err := b.Start("not json"); err == nil { t.Fatal("expected invalid JSON error") }
	if called { t.Fatal("invoker called for invalid JSON") }
}

func TestNativeFailureIsReturnedWithoutLeakingConfig(t *testing.T) {
	secretConfig := `{"password":"DO_NOT_LOG"}`
	b, _ := NewBridge(func(string) string { return `{"success":false,"data":null,"error":"invalid outbound"}` })
	err := b.Start(secretConfig)
	if err == nil || !strings.Contains(err.Error(), "invalid outbound") { t.Fatalf("unexpected error: %v", err) }
	if strings.Contains(err.Error(), "DO_NOT_LOG") { t.Fatal("error leaked config contents") }
}

func TestRejectsMalformedNativeResponse(t *testing.T) {
	b, _ := NewBridge(func(string) string { return "not-json" })
	if err := b.Stop(); err == nil { t.Fatal("expected malformed response error") }
}

func TestRequiresInvoker(t *testing.T) {
	if _, err := NewBridge(nil); err == nil { t.Fatal("expected nil invoker error") }
}
