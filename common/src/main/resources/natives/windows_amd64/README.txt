Place the extracted java-cef build for windows_amd64 here.
These files will be bundled into the mod JAR and extracted at runtime.

Expected structure (after extraction from tar.gz):
  jcef_helper.exe
  libcef.dll
  chrome_100_percent.pak
  chrome_200_percent.pak
  icudtl.dat
  libcef.lib (optional)
  ...

Use the cloneJcef gradle task, then build java-cef for this platform.
Copy the contents of the jcef_build/ output directory here.
