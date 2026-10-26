// PedIA simulada para probar PedIA Móvil en el emulador sin tocar la PedIA real.
// Imita las rutas de la API de PedIA que usa la app, una impresora ESC/POS (puerto 9100)
// y una carpeta de actualizaciones (/actualizaciones/version.json + pedia-movil.apk).
const http = require('http');
const net = require('net');
const fs = require('fs');
const path = require('path');

const PUERTO = 4100; // distinto de 4000 para no chocar con la PedIA real
const CARPETA = __dirname;
const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a);

const categorias = [
  { id: 1, nombre: 'Hamburguesas', orden: 0, activo: 1 },
  { id: 2, nombre: 'Pizzas', orden: 1, activo: 1 },
  { id: 3, nombre: 'Bebidas', orden: 2, activo: 1 },
  { id: 4, nombre: 'Postres', orden: 3, activo: 1 },
  { id: 9, nombre: 'Pedidos por teléfono (no tocar)', orden: 999, activo: 1 },
];
let pid = 1;
const P = (categoria_id, nombre, precio, extra = {}) => ({
  id: pid++, categoria_id, nombre, precio, descripcion: null, foto_path: null, disponible: 1, orden: 0, opciones: [], ...extra,
});
const productos = [
  P(1, 'Hamburguesa clásica', 950, { descripcion: 'Ternera, lechuga, tomate y salsa de la casa', opciones: ['sin queso', 'sin cebolla', 'sin pepinillo', 'poco hecha'] }),
  P(1, 'Hamburguesa doble con bacon', 1290, { opciones: ['sin queso', 'sin bacon'] }),
  P(1, 'Hamburguesa vegetal', 1050),
  P(2, 'Pizza margarita', 1100),
  P(2, 'Pizza barbacoa', 1350, { opciones: ['sin cebolla', 'extra de queso'] }),
  P(2, 'Pizza cuatro quesos', 1300, { disponible: 0 }),
  P(3, 'Coca-Cola', 250),
  P(3, 'Agua', 150),
  P(3, 'Cerveza', 300),
  P(4, 'Tarta de queso', 550),
  P(4, 'Helado', 400),
  P(9, 'Pedido telefónico (automático)', 0, { disponible: 0 }),
];
const config = [
  { clave: 'local_nombre', valor: 'Burger Pryto', cifrado: false },
  { clave: 'mesas_cantidad', valor: '12', cifrado: false },
];

let pedidos = [];
let siguientePedido = 1;
let siguienteItem = 1;
const ahoraUtc = () => new Date().toISOString().slice(0, 19).replace('T', ' ');
const completar = (p) => {
  p.total = p.items.reduce((s, i) => s + i.cantidad * i.precio_unitario, 0);
  return p;
};
const linea = (it) => {
  const prod = productos.find((x) => x.id === it.producto_id);
  if (!prod) throw Object.assign(new Error(`producto ${it.producto_id} no existe`), { status: 400 });
  if (!prod.disponible) throw Object.assign(new Error(`producto "${prod.nombre}" no está disponible`), { status: 409 });
  return { id: siguienteItem++, producto_id: prod.id, cantidad: it.cantidad || 1, precio_unitario: prod.precio, notas: it.notas || null, producto_nombre: prod.nombre };
};

// Un pedido de ejemplo ya abierto en la mesa 3, hace 25 minutos.
pedidos.push(completar({
  id: siguientePedido, numero_dia: siguientePedido++, tipo: 'mesa', mesa_numero: '3', estado: 'en_preparacion', estado_pago: 'pendiente',
  notas: 'Camarero: Ana', created_at: new Date(Date.now() - 25 * 60000).toISOString().slice(0, 19).replace('T', ' '),
  items: [linea({ producto_id: 1, cantidad: 2 }), linea({ producto_id: 7, cantidad: 2 })],
}));

function responder(res, codigo, cuerpo) {
  res.writeHead(codigo, { 'Content-Type': 'application/json' });
  res.end(cuerpo === undefined ? '' : JSON.stringify(cuerpo));
}

const servidor = http.createServer((req, res) => {
  let datos = '';
  req.on('data', (c) => (datos += c));
  req.on('end', () => {
    const url = new URL(req.url, 'http://x');
    const ruta = url.pathname;
    let body = {};
    try { body = datos ? JSON.parse(datos) : {}; } catch { /* vacío */ }
    log(req.method, req.url, datos ? datos.slice(0, 300) : '');
    try {
      if (ruta.startsWith('/actualizaciones/')) {
        const archivo = path.join(CARPETA, 'actualizaciones', path.basename(ruta));
        if (!fs.existsSync(archivo)) return responder(res, 404, { error: 'no hay' });
        const stat = fs.statSync(archivo);
        res.writeHead(200, { 'Content-Length': stat.size, 'Content-Type': ruta.endsWith('.json') ? 'application/json' : 'application/vnd.android.package-archive' });
        // Envío lento a propósito para ver la barra de progreso avanzar.
        const flujo = fs.createReadStream(archivo, { highWaterMark: 256 * 1024 });
        flujo.on('data', (trozo) => { flujo.pause(); res.write(trozo); setTimeout(() => flujo.resume(), ruta.endsWith('.apk') ? 120 : 0); });
        flujo.on('end', () => res.end());
        return;
      }
      if (ruta === '/api/health') return responder(res, 200, { ok: true });
      if (ruta === '/api/categorias') return responder(res, 200, categorias);
      if (ruta === '/api/productos') return responder(res, 200, productos);
      if (ruta === '/api/config') return responder(res, 200, config);
      if (ruta === '/api/pedidos' && req.method === 'GET') {
        const estados = (url.searchParams.get('estados') || '').split(',').filter(Boolean);
        return responder(res, 200, pedidos.filter((p) => !estados.length || estados.includes(p.estado)));
      }
      if (ruta === '/api/pedidos' && req.method === 'POST') {
        if (!body.items || !body.items.length) return responder(res, 400, { error: 'el pedido no tiene items' });
        const p = completar({
          id: siguientePedido, numero_dia: siguientePedido++, tipo: body.tipo, mesa_numero: body.mesa_numero || null,
          estado: 'pendiente', estado_pago: 'pendiente', notas: body.notas || null, created_at: ahoraUtc(), items: body.items.map(linea),
        });
        pedidos.push(p);
        log('>>> PEDIDO NUEVO', JSON.stringify(p));
        return responder(res, 201, p);
      }
      const m = ruta.match(/^\/api\/pedidos\/(\d+)(\/.*)?$/);
      if (m) {
        const p = pedidos.find((x) => x.id === Number(m[1]));
        if (!p) return responder(res, 404, { error: 'pedido no encontrado' });
        const resto = m[2] || '';
        let mi;
        if (resto === '/items' && req.method === 'POST') {
          const nueva = linea(body);
          const igual = p.items.find((i) => i.producto_id === nueva.producto_id && (i.notas || null) === nueva.notas);
          if (igual) igual.cantidad += nueva.cantidad; else p.items.push(nueva);
          log('>>> AÑADIDO A PEDIDO', p.id, JSON.stringify(body));
          return responder(res, 201, completar(p));
        }
        if ((mi = resto.match(/^\/items\/(\d+)$/))) {
          const item = p.items.find((i) => i.id === Number(mi[1]));
          if (!item) return responder(res, 404, { error: 'línea de pedido no encontrada' });
          if (req.method === 'DELETE') p.items = p.items.filter((i) => i !== item);
          else if (body.cantidad != null) item.cantidad = Number(body.cantidad);
          return responder(res, 200, completar(p));
        }
        if (resto === '/marcar-pagado') { p.estado_pago = 'pagado'; return responder(res, 200, p); }
        if (resto === '/liberar-mesa') { p.mesa_numero = null; return responder(res, 200, p); }
        if (resto === '/mesa') { p.mesa_numero = String(body.mesa_numero); return responder(res, 200, p); }
        if (resto === '/reimprimir') { log('>>> REIMPRIMIR', p.id); return responder(res, 200, { ok: true }); }
      }
      responder(res, 404, { error: 'ruta no simulada' });
    } catch (err) {
      responder(res, err.status || 500, { error: err.message });
    }
  });
});
servidor.listen(PUERTO, () => log(`PedIA simulada en :${PUERTO}`));

// Impresora ESC/POS simulada: guarda cada ticket recibido en tickets/ y lo muestra en texto.
fs.mkdirSync(path.join(CARPETA, 'tickets'), { recursive: true });
net.createServer((socket) => {
  const trozos = [];
  socket.on('data', (d) => trozos.push(d));
  socket.on('end', () => {
    const datos = Buffer.concat(trozos);
    const nombre = path.join(CARPETA, 'tickets', `ticket-${Date.now()}.bin`);
    fs.writeFileSync(nombre, datos);
    const texto = Buffer.from(datos.filter((b) => b === 0x0a || (b >= 0x20 && b !== 0x7f))).toString('latin1');
    log(`>>> TICKET IMPRESO (${datos.length} bytes) -> ${nombre}\n${texto}`);
  });
}).listen(9100, () => log('Impresora simulada en :9100'));
