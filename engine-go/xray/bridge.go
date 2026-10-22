// Package xray defines a small, testable boundary around the official libXray Invoke API.
// Android builds must provide libXray.Invoke as the Invoker and bundle exactly one Go runtime.
package xray

import (
	"encoding/json"
	"errors"
	"fmt"
	"strings"
)

const APIVersion = 3
const maxRequestBytes = 16 << 20

type Invoker func(requestJSON string) string

type Bridge struct { invoke Invoker }

func NewBridge(invoke Invoker) (*Bridge, error) {
	if invoke == nil { return nil, errors.New("libXray invoker is required") }
	return &Bridge{invoke: invoke}, nil
}

type request struct {
	APIVersion int             `json:"apiVersion"`
	Method     string          `json:"method"`
	Payload    json.RawMessage `json:"payload"`
}
type response struct {
	Success bool            `json:"success"`
	Data    json.RawMessage `json:"data"`
	Error   string          `json:"error"`
}

func (b *Bridge) call(method string, payload any) (json.RawMessage, error) {
	if b == nil || b.invoke == nil { return nil, errors.New("libXray bridge is not initialized") }
	if strings.TrimSpace(method) == "" { return nil, errors.New("method is required") }
	body, err := json.Marshal(payload); if err != nil { return nil, fmt.Errorf("encode payload: %w", err) }
	req, err := json.Marshal(request{APIVersion: APIVersion, Method: method, Payload: body}); if err != nil { return nil, fmt.Errorf("encode request: %w", err) }
	if len(req) > maxRequestBytes { return nil, errors.New("libXray request exceeds 16 MiB limit") }
	raw := b.invoke(string(req))
	if strings.TrimSpace(raw) == "" { return nil, errors.New("libXray returned an empty response") }
	if len(raw) > maxRequestBytes { return nil, errors.New("libXray response exceeds 16 MiB limit") }
	var out response
	if err := json.Unmarshal([]byte(raw), &out); err != nil { return nil, errors.New("libXray returned malformed response JSON") }
	if !out.Success { msg := strings.TrimSpace(out.Error); if msg == "" { msg = "libXray operation failed" }; if len(msg) > 240 { msg = msg[:240] }; return nil, errors.New(msg) }
	return out.Data, nil
}

// Start passes validated Xray JSON to libXray. It does not claim the Android TUN is routed.
func (b *Bridge) Start(xrayJSON string) error {
	if strings.TrimSpace(xrayJSON) == "" { return errors.New("Xray config is empty") }
	if !json.Valid([]byte(xrayJSON)) { return errors.New("Xray config is not valid JSON") }
	_, err := b.call("runXray", map[string]string{"xrayJson": xrayJSON})
	return err
}

func (b *Bridge) Stop() error { _, err := b.call("stopXray", map[string]any{}); return err }

func (b *Bridge) Version() (string, error) {
	data, err := b.call("xrayVersion", map[string]any{}); if err != nil { return "", err }
	var value struct { Version string `json:"version"` }
	if err := json.Unmarshal(data, &value); err != nil { return "", errors.New("libXray version response is malformed") }
	if value.Version == "" { return "", errors.New("libXray version is missing") }
	return value.Version, nil
}
