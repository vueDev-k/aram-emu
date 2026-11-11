//go:build android

package mobile

import "github.com/mirusu400/aram-frontend/frontend"

// Android uses the existing native import flow when a title is opened from
// the Home library. Folder scanning only sends metadata to this launcher.
type androidLibraryHost interface {
    RequestGameLibraryFolder()
    ForgetGameLibraryFolder()
    OpenLibraryGame(uri string)
}

func platformSetAndroidLibraryHost(host Host) {
    native, ok := host.(androidLibraryHost)
    if !ok {
        frontend.SetNativeAndroidLibraryHost(nil)
        return
    }
    frontend.SetNativeAndroidLibraryHost(native)
}

// UpdateGameLibrary sends SAF folder scan results into the existing Go Home
// screen. Game bytes stay untouched until the user selects one, at which point
// Android's original importDocument flow handles the file.
func UpdateGameLibrary(entriesJSON, folderLabel string) string {
    if err := frontend.SubmitAndroidGameLibrary(entriesJSON, folderLabel); err != nil {
        return err.Error()
    }
    return ""
}
