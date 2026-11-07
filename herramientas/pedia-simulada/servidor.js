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
  { id: 5, nombre: 'Menús', orden: -1, activo: 1 },
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
  P(5, 'Menú Hamburguesa', 900, {
    descripcion: 'Hamburguesa, patatas y bebida',
    grupos: [
      { id: 1, nombre: 'Hamburguesa', obligatorio: true, max_selecciones: 1, opciones: [
        { id: 11, texto: 'Hamburguesa clásica', suplemento: 0, producto_id: null, agotada: false },
        { id: 12, texto: 'Doble carne', suplemento: 200, producto_id: 2, agotada: false },
        { id: 13, texto: 'Pollo crispy', suplemento: 0, producto_id: null, agotada: true },
      ] },
      { id: 2, nombre: 'Bebida', obligatorio: true, max_selecciones: 1, opciones: [
        { id: 21, texto: 'Coca-Cola', suplemento: 0, producto_id: 7, agotada: false },
        { id: 22, texto: 'Agua', suplemento: 0, producto_id: 8, agotada: false },
        { id: 23, texto: 'Cerveza', suplemento: 50, producto_id: 9, agotada: false },
      ] },
      { id: 3, nombre: 'Extras', obligatorio: false, max_selecciones: 2, opciones: [
        { id: 31, texto: 'Bacon', suplemento: 100, producto_id: null, agotada: false },
        { id: 32, texto: 'Queso extra', suplemento: 50, producto_id: null, agotada: false },
      ] },
    ],
  }),
];
for (const p of productos) if (!p.grupos) p.grupos = [];
const config = [
  { clave: 'local_nombre', valor: 'Burger Pryto', cifrado: false },
  // Mesas por zonas, como PedIA: T1..T4 (terraza), S1..S6 (salón) y B1..B3 (barra). mesas_cantidad es la suma.
  { clave: 'mesas_terraza', valor: '4', cifrado: false },
  { clave: 'mesas_salon', valor: '6', cifrado: false },
  { clave: 'mesas_barra', valor: '3', cifrado: false },
  { clave: 'mesas_cantidad', valor: '13', cifrado: false },
  { clave: 'iva_porcentaje', valor: '10', cifrado: false },
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
  const elegidas = prod.grupos.flatMap((g) => g.opciones).filter((o) => (it.selecciones || []).includes(o.id));
  const faltan = prod.grupos.filter((g) => g.obligatorio && !g.opciones.some((o) => elegidas.includes(o)));
  if (faltan.length) throw Object.assign(new Error(`Falta elegir: ${faltan.map((g) => g.nombre).join(', ')}`), { status: 400 });
  const notas = [...elegidas.map((o) => o.texto), ...(it.notas ? [it.notas] : [])].join(', ') || null;
  const precio = prod.precio + elegidas.reduce((s, o) => s + o.suplemento, 0);
  return { id: siguienteItem++, producto_id: prod.id, cantidad: it.cantidad || 1, precio_unitario: precio, notas, producto_nombre: prod.nombre, producto_iva_porcentaje: null };
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
      let mp;
      if ((mp = ruta.match(/^\/pago\/(\d+)\/tarjeta$/))) {
        const id = mp[1];
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        return res.end(`<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><meta charset="utf-8"></head>
<body style="font-family:sans-serif;text-align:center;padding:40px;background:#f4f6f9">
<h2>Pasarela de pago SIMULADA</h2><p>Pedido ${id} · en la real aquí sale el formulario de tarjeta de Redsys</p>
<p><a id="pagar" href="/pago/${id}/simular-ok" style="display:inline-block;padding:18px 40px;background:#2f6fed;color:#fff;border-radius:12px;font-size:22px;text-decoration:none">Pagar</a></p>
<p><a id="cancelar" href="/pago/ko" style="font-size:20px">Cancelar</a></p></body></html>`);
      }
      if ((mp = ruta.match(/^\/pago\/(\d+)\/simular-ok$/))) {
        const p = pedidos.find((x) => x.id === Number(mp[1]));
        if (p) {
          p.estado_pago = 'pagado'; p.metodo_pago = 'tarjeta'; log('>>> PAGADO CON TARJETA (simulado)', p.id);
          if (p.cocina_retenida) { p.cocina_retenida = 0; log('>>> COMANDA A COCINA', p.id, '(al cobrarse)'); }
        }
        res.writeHead(302, { Location: '/pago/ok' });
        return res.end();
      }
      if (ruta === '/pago/ok' || ruta === '/pago/ko') {
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        return res.end(ruta === '/pago/ok' ? '<h1>Pago realizado</h1>' : '<h1>Pago no completado</h1>');
      }
      // Para probar cambios de ajustes en PedIA (p. ej. las mesas de una zona):
      //   curl -X POST http://localhost:4100/simular-config -d '{"clave":"mesas_barra","valor":"5"}'
      if (ruta === '/simular-config' && req.method === 'POST') {
        const c = config.find((x) => x.clave === body.clave);
        if (c) c.valor = String(body.valor); else config.push({ clave: body.clave, valor: String(body.valor), cifrado: false });
        log('>>> AJUSTE CAMBIADO', body.clave, '=', body.valor);
        return responder(res, 200, { ok: true });
      }
      // Para probar el pago con el móvil: curl -X POST http://localhost:4100/simular-pago/ID
      if ((mp = ruta.match(/^\/simular-pago\/(\d+)$/))) {
        const p = pedidos.find((x) => x.id === Number(mp[1]));
        if (!p) return responder(res, 404, { error: 'no existe' });
        p.estado_pago = 'pagado'; p.metodo_pago = 'tarjeta';
        log('>>> PAGADO CON EL MÓVIL (simulado)', p.id);
        if (p.cocina_retenida) { p.cocina_retenida = 0; log('>>> COMANDA A COCINA', p.id, '(al cobrarse)'); }
        return responder(res, 200, { ok: true });
      }
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
          origen: body.origen || 'tpv', pago_previsto: body.pago_previsto || null, cocina_retenida: body.retener_cocina ? 1 : 0,
        });
        pedidos.push(p);
        log('>>> PEDIDO NUEVO', JSON.stringify(p));
        if (!p.cocina_retenida) log('>>> COMANDA A COCINA', p.id, '(al crear el pedido)');
        return responder(res, 201, p);
      }
      const m = ruta.match(/^\/api\/pedidos\/(\d+)(\/.*)?$/);
      if (m) {
        const p = pedidos.find((x) => x.id === Number(m[1]));
        if (!p) return responder(res, 404, { error: 'pedido no encontrado' });
        const resto = m[2] || '';
        let mi;
        if (resto === '' && req.method === 'GET') return responder(res, 200, p);
        if (resto === '' && req.method === 'DELETE') {
          pedidos = pedidos.filter((x) => x !== p);
          log('>>> PEDIDO BORRADO', p.id);
          return responder(res, 204);
        }
        if (resto === '/liberar-cocina') {
          p.pago_previsto = body.pago_previsto || p.pago_previsto;
          if (p.cocina_retenida) { p.cocina_retenida = 0; log('>>> COMANDA A COCINA', p.id, '(pagará en caja)'); }
          return responder(res, 200, p);
        }
        if (resto === '/pago-qr') return responder(res, 200, { url: `http://10.0.2.2:${PUERTO}/pago/${p.id}`, qrDataUrl: '' });
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
          else { item.notas = (body.notas || '').trim() || null; log('>>> NOTA CAMBIADA', p.id, item.id, item.notas); }
          return responder(res, 200, completar(p));
        }
        if (resto === '/marcar-pagado') { p.estado_pago = 'pagado'; return responder(res, 200, p); }
        if (resto === '/liberar-mesa') { p.mesa_numero = null; return responder(res, 200, p); }
        if (resto === '/mesa') { p.mesa_numero = String(body.mesa_numero); return responder(res, 200, p); }
        if (resto === '/reimprimir') {
          // Con un archivo "impresora-rota" en esta carpeta, la impresora simulada falla (PedIA contesta bien y lo anota en el pedido).
          const rota = fs.existsSync(path.join(CARPETA, 'impresora-rota'));
          p.impresion = rota ? { estado: 'error', ultimo_error: 'Impresora desconectada' } : { estado: 'enviado', ultimo_error: null };
          log(body.solo_principal ? '>>> TICKET PRINCIPAL (en cocina nada)' : '>>> REIMPRIMIR CON COCINAS', p.id, rota ? 'FALLA LA IMPRESORA' : '');
          return responder(res, 200, { ok: true });
        }
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
