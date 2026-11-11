#!/usr/bin/env python3
"""Patch only the pinned frontend checkout used by Android APK builds.

The public upstream frontend is left unchanged. Every replacement is guarded:
a frontend revision drift fails the build rather than silently omitting Android
library support.
"""
from pathlib import Path
import shutil
import sys

frontend = Path(sys.argv[1]).resolve()
product = Path(__file__).resolve().parents[1]


def replace_file(relative: str, old: str, new: str) -> None:
    file = frontend / "frontend" / relative
    content = file.read_text(encoding="utf-8")
    if content.count(old) != 1:
        raise RuntimeError(f"{relative}: expected exactly one Android integration anchor, found {content.count(old)}")
    file.write_text(content.replace(old, new, 1), encoding="utf-8")


replace_file(
    "picker_native.go",
    'func (nativeHostPicker) OpenGameDirectory(string) (string, error) {\n\treturn "", ErrPickerUnavailable\n}',
    'func (nativeHostPicker) OpenGameDirectory(string) (string, error) {\n\treturn "", requestNativeGameLibraryFolder()\n}',
)
replace_file(
    "shell_results.go",
    '\t\tcase entries := <-s.libraryResults:\n\t\t\ts.libraryScanning = false',
    '\t\tcase snapshot := <-androidLibraryUpdates:\n'
    '\t\t\ts.libraryScanning = false\n'
    '\t\t\ts.libraryEntries = snapshot.entries\n'
    '\t\t\tandroidFolderLabel = snapshot.folder\n'
    '\t\t\ts.homeTab = homeTabInstalled\n'
    '\t\t\ts.dialogOpen = false\n'
    '\t\t\ts.state = s.preDialogState\n'
    '\t\tcase entries := <-s.libraryResults:\n\t\t\ts.libraryScanning = false',
)
replace_file(
    "shell_library.go",
    'func (s *Shell) homeLibraryFolders() []string {\n\treturn s.settings.GameLibraryFolders\n}',
    'func (s *Shell) homeLibraryFolders() []string {\n'
    '\tif androidFolderLabel != "" {\n'
    '\t\treturn []string{androidFolderLabel}\n'
    '\t}\n'
    '\treturn s.settings.GameLibraryFolders\n}',
)
replace_file(
    "shell_library.go",
    'func (s *Shell) removeLibraryFolderPath(path string) {\n\tif !s.settings.removeLibraryFolder(path) {',
    'func (s *Shell) removeLibraryFolderPath(path string) {\n'
    '\tif androidFolderLabel != "" && path == androidFolderLabel {\n'
    '\t\trequestForgetAndroidLibraryFolder()\n'
    '\t\treturn\n'
    '\t}\n'
    '\tif !s.settings.removeLibraryFolder(path) {',
)
replace_file(
    "settings_library.go",
    'func cleanLibraryPath(path string) string {\n\tpath = strings.TrimSpace(path)\n\tif path == "" {\n\t\treturn ""\n\t}',
    'func cleanLibraryPath(path string) string {\n'
    '\tpath = strings.TrimSpace(path)\n'
    '\tif path == "" {\n\t\treturn ""\n\t}\n'
    '\tif strings.HasPrefix(path, "content://") {\n'
    '\t\treturn path\n\t}',
)
replace_file(
    "settings.go",
    'func (s *Settings) addRecent(path, name, sha256 string) {\n'
    '\tabsolute, err := filepath.Abs(path)\n'
    '\tif err == nil {\n\t\tpath = absolute\n\t}\n'
    '\tpath = filepath.Clean(path)',
    'func (s *Settings) addRecent(path, name, sha256 string) {\n'
    '\tif !strings.HasPrefix(path, "content://") {\n'
    '\t\tabsolute, err := filepath.Abs(path)\n'
    '\t\tif err == nil {\n\t\t\tpath = absolute\n\t\t}\n'
    '\t\tpath = filepath.Clean(path)\n'
    '\t}',
)
replace_file(
    "settings.go",
    '\t\tif strings.EqualFold(filepath.Clean(existing.Path), path) {\n\t\t\treturn true\n\t\t}',
    '\t\tif strings.HasPrefix(path, "content://") || strings.HasPrefix(existing.Path, "content://") {\n'
    '\t\t\tif strings.EqualFold(existing.Path, path) {\n\t\t\t\treturn true\n\t\t\t}\n'
    '\t\t} else if strings.EqualFold(filepath.Clean(existing.Path), path) {\n'
    '\t\t\treturn true\n\t\t}',
)
# Android's native SAF folder is not in desktop GameLibraryFolders. Closing
# a game must not run the desktop rescan (which would wipe the SAF snapshot).
replace_file(
    "shell_media.go",
    '\t// Closing a title returns to the Home surface; refresh the Installed tab in\n'
    '\t// case the library changed while a title was open.\n'
    '\ts.rescanLibrary()',
    '\t// Keep the native SAF library already scanned on Android.\n'
    '\tif androidFolderLabel == "" {\n'
    '\t\ts.rescanLibrary()\n'
    '\t}',
)

# Keep the existing launcher and opener. Android library rows are SAF URIs,
# so selecting one must invoke the *existing* native document-import flow,
# which imports on a worker and then calls Mobile.openDocument as before.
replace_file(
    "shell_library.go",
    's.openRequest(OpenRequest{Path: path, DisplayName: s.rememberedDisplayName(path)})',
    'if strings.HasPrefix(path, "content://") {\n'
    '\t\tif !requestNativeOpenLibraryGame(path) {\n'
    '\t\t\ts.setStatus(s.tr("Use the native mobile document picker"))\n'
    '\t\t}\n'
    '\t\treturn\n'
    '\t}\n'
    '\ts.openRequest(OpenRequest{Path: path, DisplayName: s.rememberedDisplayName(path)})',
)
source = product / "android" / "frontend-overlay" / "android_library_android.go.src"
destination = frontend / "frontend" / "android_library_android.go"
shutil.copyfile(source, destination)
print(f"Android library bridge integrated into {frontend}")
