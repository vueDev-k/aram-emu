//go:build android

package mobile

import (
    "context"
    "errors"
    "fmt"
    "strings"

    "github.com/mirusu400/aram-frontend/frontend"
)

// Only Android has a native SAF tree and content:// document descriptors.
// iOS and desktop builds retain their original backend implementation.
type androidLibraryHost interface {
    RequestGameLibraryFolder()
    ForgetGameLibraryFolder()
    OpenLibraryFile(uri string) (string, error)
    CloseLibraryFile(path string)
}

func platformSetAndroidLibraryHost(host Host) {
    native, ok := host.(androidLibraryHost)
    if !ok {
        frontend.SetNativeAndroidLibraryHost(nil)
        return
    }
    frontend.SetNativeAndroidLibraryHost(native)
}

func (backend *productBackend) Open(
    ctx context.Context, request frontend.OpenRequest,
) (frontend.InputInfo, error) {
    return backend.OpenWithProgress(ctx, request, nil)
}

// Preserve the URI as the frontend's recent/favorite identity while opening
// the original ZIP through a borrowed descriptor; no ZIP bytes are copied.
func (backend *productBackend) OpenWithProgress(
    ctx context.Context, request frontend.OpenRequest, progress func(frontend.OpenStage),
) (frontend.InputInfo, error) {
    if !strings.HasPrefix(request.Path, "content://") {
        return backend.Backend.OpenWithProgress(ctx, request, progress)
    }
    host, ok := currentHost().(androidLibraryHost)
    if !ok {
        return frontend.InputInfo{}, errors.New("Android game library host is unavailable")
    }
    original := request.Path
    if request.DisplayName == "" {
        request.DisplayName = frontend.AndroidLibraryDisplayName(original)
    }
    borrowedPath, err := host.OpenLibraryFile(original)
    if err != nil {
        return frontend.InputInfo{}, fmt.Errorf("open Android game document: %w", err)
    }
    defer host.CloseLibraryFile(borrowedPath)
    request.Path = borrowedPath
    return backend.Backend.OpenWithProgress(ctx, request, progress)
}

// The SDK sends metadata only. The frontend Update loop owns the on-screen
// list, and the host keeps the persisted SAF grant for subsequent launches.
func UpdateGameLibrary(entriesJSON, folderLabel string) string {
    if err := frontend.SubmitAndroidGameLibrary(entriesJSON, folderLabel); err != nil {
        return err.Error()
    }
    return ""
}

// Icon is also requested by the shared Home launcher. Resolve SAF URIs here
// so the existing icon tiles and "Create shortcut" action keep working.
func (backend *productBackend) Icon(path string) ([]byte, error) {
    if !strings.HasPrefix(path, "content://") {
        return backend.Backend.Icon(path)
    }
    host, ok := currentHost().(androidLibraryHost)
    if !ok {
        return nil, errors.New("Android game library host is unavailable")
    }
    borrowed, err := host.OpenLibraryFile(path)
    if err != nil {
        return nil, err
    }
    defer host.CloseLibraryFile(borrowed)
    return backend.Backend.Icon(borrowed)
}
