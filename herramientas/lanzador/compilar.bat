@echo off
rem Compila "Abrir tablet de pruebas.exe" (en la carpeta del proyecto) con el compilador de C#
rem que viene con Windows. Solo hace falta repetirlo si se cambia Lanzador.cs o el icono.
"%WINDIR%\Microsoft.NET\Framework64\v4.0.30319\csc.exe" /nologo /codepage:65001 /optimize ^
  /target:winexe /win32icon:"%~dp0pedia.ico" ^
  /reference:System.Windows.Forms.dll /reference:System.Drawing.dll /reference:System.Core.dll ^
  /out:"%~dp0..\..\Abrir tablet de pruebas.exe" "%~dp0Lanzador.cs"
