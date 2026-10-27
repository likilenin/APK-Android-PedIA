// "Abrir tablet de pruebas.exe": con un doble clic arranca el PedIA simulado, abre la tablet
// de pruebas (emulador Android) e instala y abre PedIA Móvil, enseñando en una ventanita qué
// está haciendo. Se compila con compilar.bat (usa el compilador de C# que trae Windows).
using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Net.Sockets;
using System.Threading;
using System.Windows.Forms;

static class Lanzador
{
    const int PuertoSimulado = 4100;
    const string Avd = "pedia_tablet";
    const string Paquete = "com.pedia.movil";

    static Form ventana;
    static Label titulo;
    static Label detalle;
    static ProgressBar barra;
    static Button cerrar;

    static readonly string Carpeta = AppDomain.CurrentDomain.BaseDirectory;
    static readonly string Sdk = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Android", "sdk");
    static readonly string Adb = Path.Combine(Sdk, "platform-tools", "adb.exe");
    static readonly string Emulador = Path.Combine(Sdk, "emulator", "emulator.exe");

    [STAThread]
    static void Main()
    {
        Application.EnableVisualStyles();
        CrearVentana();
        new Thread(Trabajar) { IsBackground = true }.Start();
        Application.Run(ventana);
    }

    static void CrearVentana()
    {
        ventana = new Form
        {
            Text = "PedIA Móvil - tablet de pruebas",
            ClientSize = new Size(480, 190),
            FormBorderStyle = FormBorderStyle.FixedDialog,
            MaximizeBox = false,
            StartPosition = FormStartPosition.CenterScreen,
            BackColor = Color.FromArgb(15, 23, 42),
            Font = new Font("Segoe UI", 10f),
        };
        try { ventana.Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }

        titulo = new Label
        {
            Text = "Preparando…",
            ForeColor = Color.White,
            Font = new Font("Segoe UI Semibold", 15f),
            Location = new Point(24, 22),
            Size = new Size(432, 34),
        };
        detalle = new Label
        {
            Text = "",
            ForeColor = Color.FromArgb(203, 213, 225),
            Location = new Point(24, 60),
            Size = new Size(432, 44),
        };
        barra = new ProgressBar
        {
            Location = new Point(24, 110),
            Size = new Size(432, 14),
            Maximum = 4,
        };
        cerrar = new Button
        {
            Text = "Cerrar",
            Location = new Point(366, 140),
            Size = new Size(90, 32),
            Visible = false,
            BackColor = Color.FromArgb(47, 111, 237),
            ForeColor = Color.White,
            FlatStyle = FlatStyle.Flat,
        };
        cerrar.FlatAppearance.BorderSize = 0;
        cerrar.Click += delegate { ventana.Close(); };
        ventana.Controls.AddRange(new Control[] { titulo, detalle, barra, cerrar });
    }

    static void Mostrar(int paso, string textoTitulo, string textoDetalle)
    {
        ventana.BeginInvoke((Action)delegate
        {
            barra.Value = Math.Min(paso, barra.Maximum);
            titulo.Text = textoTitulo;
            detalle.Text = textoDetalle;
        });
    }

    static void Terminar(bool bien, string textoTitulo, string textoDetalle)
    {
        ventana.BeginInvoke((Action)delegate
        {
            titulo.Text = textoTitulo;
            titulo.ForeColor = bien ? Color.FromArgb(74, 222, 128) : Color.FromArgb(248, 113, 113);
            detalle.Text = textoDetalle;
            if (bien) barra.Value = barra.Maximum;
            cerrar.Visible = true;
        });
        if (bien)
        {
            Thread.Sleep(5000);
            ventana.BeginInvoke((Action)delegate { ventana.Close(); });
        }
    }

    static void Trabajar()
    {
        try
        {
            if (!File.Exists(Adb) || !File.Exists(Emulador))
            {
                Terminar(false, "Falta el Android SDK", "No se encuentra en " + Sdk + ".");
                return;
            }

            // 1. PedIA simulado (ventana minimizada; cerrarla lo apaga).
            Mostrar(0, "Arrancando el PedIA simulado…", "Carta de prueba, 12 mesas e impresora simulada (puerto 4100).");
            if (!PuertoAbierto(PuertoSimulado))
            {
                string servidor = Path.Combine(Carpeta, "herramientas", "pedia-simulada", "servidor.js");
                Process.Start(new ProcessStartInfo("cmd.exe",
                    "/k title PedIA simulado - no cerrar mientras pruebas & node \"" + servidor + "\"")
                {
                    UseShellExecute = true,
                    WindowStyle = ProcessWindowStyle.Minimized,
                    WorkingDirectory = Carpeta,
                });
            }

            // 2. Tablet de pruebas.
            Mostrar(1, "Abriendo la tablet de pruebas…", "Emulador Android " + Avd + ".");
            // (adb puede añadir delante avisos de su propio arranque: se mira el final)
            if (!Ejecutar(Adb, "get-state", 15000).Trim().EndsWith("device"))
            {
                Process.Start(new ProcessStartInfo(Emulador, "-avd " + Avd)
                {
                    UseShellExecute = false,
                    CreateNoWindow = true,
                    WorkingDirectory = Path.GetDirectoryName(Emulador),
                });
            }

            // 3. Esperar a que Android termine de arrancar.
            Mostrar(2, "Arrancando Android…", "La primera vez puede tardar un par de minutos.");
            DateTime limite = DateTime.Now.AddMinutes(6);
            while (!Ejecutar(Adb, "shell getprop sys.boot_completed", 10000).Trim().EndsWith("1"))
            {
                if (DateTime.Now > limite)
                {
                    Terminar(false, "Android no ha arrancado", "Cierra la ventana del emulador y vuelve a intentarlo.");
                    return;
                }
                Thread.Sleep(2000);
            }

            // 4. Instalar la APK de la carpeta (la más reciente) y abrir la app.
            string apk = ApkMasReciente();
            Mostrar(3, "Instalando PedIA Móvil…", apk != null ? Path.GetFileName(apk) : "No hay APK en la carpeta: se abre la ya instalada.");
            if (apk != null)
            {
                // Sin forzar: si la tablet ya tiene una versión más nueva (actualizada desde
                // GitHub), Android rechaza la instalación y se queda con la nueva.
                Ejecutar(Adb, "install -r \"" + apk + "\"", 180000);
            }
            string salida = Ejecutar(Adb, "shell am start -n " + Paquete + "/.MainActivity", 20000);
            if (salida.Contains("Error"))
            {
                Terminar(false, "No se ha podido abrir la app", "¿Está instalada? Pon la APK en la carpeta y vuelve a abrir esto.");
                return;
            }
            Terminar(true, "¡Listo! La app está abierta", "Atajos en la tablet: Ctrl+Retroceso = atrás, Ctrl+H = inicio. Esta ventana se cierra sola.");
        }
        catch (Exception e)
        {
            Terminar(false, "Algo ha fallado", e.Message);
        }
    }

    static string ApkMasReciente()
    {
        string enCarpeta = Directory.GetFiles(Carpeta, "PedIA-Movil-*.apk")
            .OrderByDescending(f => File.GetLastWriteTime(f))
            .FirstOrDefault();
        if (enCarpeta != null) return enCarpeta;
        string compilada = Path.Combine(Carpeta, "app", "build", "outputs", "apk", "release", "app-release.apk");
        return File.Exists(compilada) ? compilada : null;
    }

    static bool PuertoAbierto(int puerto)
    {
        try
        {
            using (var cliente = new TcpClient())
            {
                return cliente.ConnectAsync("127.0.0.1", puerto).Wait(800) && cliente.Connected;
            }
        }
        catch
        {
            return false;
        }
    }

    static string Ejecutar(string programa, string argumentos, int tiempoMaximoMs)
    {
        var info = new ProcessStartInfo(programa, argumentos)
        {
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
        };
        using (var proceso = Process.Start(info))
        {
            var salida = proceso.StandardOutput.ReadToEndAsync();
            var errores = proceso.StandardError.ReadToEndAsync();
            if (!proceso.WaitForExit(tiempoMaximoMs))
            {
                try { proceso.Kill(); } catch { }
                return "";
            }
            return salida.Result + errores.Result;
        }
    }
}
