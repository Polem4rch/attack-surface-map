package io.github.surfacemap;

import io.github.surfacemap.SurfaceMapModel.EndpointEntry;

import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Builds the self-contained HTML map file by injecting live endpoint data
 * into the embedded template.  No I/O here – just string building.
 */
public class HtmlRenderer {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // The complete HTML template with /*__DATA__*/ as the injection point.
    private static final String TEMPLATE =
            "<meta charset=\"utf-8\">\n" +
            "<title>Attack Surface Map</title>\n" +
            "<style>\n" +
            "  :root{\n" +
            "    --bg:#0a0e1a; --panel:#10182e; --panel-2:#0d1426;\n" +
            "    --ink:#e7ecff; --muted:#7c87b5; --line:#283255;\n" +
            "    --new:#ffb454;\n" +
            "    --auth:#7aa2f7; --account:#9ece6a; --money:#f7768e;\n" +
            "    --commerce:#e0af68; --data:#bb9af7; --admin:#ff9e64; --other:#566089;\n" +
            "  }\n" +
            "  *{box-sizing:border-box}\n" +
            "  body{margin:0;background:radial-gradient(1200px 600px at 80% -10%, #16213f 0%, transparent 60%),var(--bg);\n" +
            "    color:var(--ink);font-family:ui-sans-serif,system-ui,-apple-system,\"Segoe UI\",Roboto,sans-serif;-webkit-font-smoothing:antialiased}\n" +
            "  .wrap{max-width:1320px;margin:0 auto;padding:24px 20px 40px}\n" +
            "  header{display:flex;flex-wrap:wrap;align-items:flex-end;gap:18px;justify-content:space-between;border-bottom:1px solid var(--line);padding-bottom:16px;margin-bottom:16px}\n" +
            "  .eyebrow{font-family:ui-monospace,\"SF Mono\",Menlo,monospace;font-size:11px;letter-spacing:.22em;text-transform:uppercase;color:var(--muted)}\n" +
            "  h1{margin:6px 0 0;font-size:24px;font-weight:650;letter-spacing:-.01em}\n" +
            "  h1 .dom{font-family:ui-monospace,\"SF Mono\",Menlo,monospace;color:var(--auth)}\n" +
            "  .meta{font-family:ui-monospace,\"SF Mono\",Menlo,monospace;font-size:12px;color:var(--muted);text-align:right;line-height:1.7}\n" +
            "  .meta b{color:var(--ink);font-weight:600}\n" +
            "  .meta .n{color:var(--new)}\n" +
            "  .bar{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-bottom:14px}\n" +
            "  button{font:inherit;font-size:13px;color:var(--ink);background:var(--panel);border:1px solid var(--line);border-radius:8px;padding:7px 12px;cursor:pointer;transition:.15s}\n" +
            "  button:hover{border-color:var(--auth)}\n" +
            "  .ico{width:34px;text-align:center;font-size:15px;padding:7px 0}\n" +
            "  .seg-group{display:inline-flex;border:1px solid var(--line);border-radius:8px;overflow:hidden}\n" +
            "  .seg-group .seg{border:0;border-radius:0;background:var(--panel-2)}\n" +
            "  .seg-group .seg.active{background:var(--auth);color:#0a0e1a;font-weight:600}\n" +
            "  .sep{width:1px;height:24px;background:var(--line);margin:0 4px}\n" +
            "  .legend{display:flex;flex-wrap:wrap;gap:12px;margin-left:auto;font-family:ui-monospace,monospace;font-size:11px;color:var(--muted)}\n" +
            "  .legend span{display:inline-flex;align-items:center;gap:6px}\n" +
            "  .dot{width:9px;height:9px;border-radius:2px;display:inline-block}\n" +
            "  .stage{display:flex;gap:14px;align-items:stretch}\n" +
            "  .map{flex:1;min-width:0;height:76vh;min-height:540px;position:relative;overflow:hidden;background:linear-gradient(180deg,var(--panel-2),#0a1020);border:1px solid var(--line);border-radius:14px;cursor:grab}\n" +
            "  .map.grabbing{cursor:grabbing}\n" +
            "  .map svg{display:block}\n" +
            "  .panel{width:320px;flex:none;background:linear-gradient(180deg,var(--panel),var(--panel-2));border:1px solid var(--line);border-radius:14px;padding:16px;font-size:13px;overflow:auto;max-height:76vh}\n" +
            "  .panel .kind{font-family:ui-monospace,monospace;font-size:11px;color:var(--muted);letter-spacing:.14em;text-transform:uppercase}\n" +
            "  .panel .ttl{font-weight:650;font-size:15px;margin-top:2px;word-break:break-all}\n" +
            "  .panel .url{font-family:ui-monospace,monospace;font-size:12px;background:var(--bg);border:1px solid var(--line);border-radius:8px;padding:8px 10px;margin:12px 0 8px;word-break:break-all;user-select:all}\n" +
            "  .panel .row{display:flex;gap:8px;align-items:center;margin:8px 0}\n" +
            "  .panel .muted{color:var(--muted);font-family:ui-monospace,monospace;font-size:12px}\n" +
            "  .panel .new-tag{color:var(--new);font-weight:700;font-size:11px;letter-spacing:.08em}\n" +
            "  .panel h4{margin:16px 0 8px;font-size:12px;color:var(--muted);font-weight:600;border-top:1px solid var(--line);padding-top:12px}\n" +
            "  .eplist{display:flex;flex-direction:column;gap:6px}\n" +
            "  .epitem{display:flex;gap:8px;align-items:center;justify-content:space-between;font-family:ui-monospace,monospace;font-size:11.5px;background:var(--bg);border:1px solid var(--line);border-radius:7px;padding:6px 8px}\n" +
            "  .epitem span{word-break:break-all}\n" +
            "  .epitem.new{border-color:var(--new)}\n" +
            "  .icp{font:inherit;font-size:11px;padding:4px 9px;border:1px solid var(--line);background:var(--panel);border-radius:6px;color:var(--ink);cursor:pointer;flex:none}\n" +
            "  .icp:hover{border-color:var(--auth)}\n" +
            "  .pwrap{display:flex;flex-wrap:wrap;margin-top:4px}\n" +
            "  .pchip{font-family:ui-monospace,monospace;font-size:11px;padding:3px 8px;margin:0 6px 6px 0;border:1px solid var(--line);background:var(--bg);color:var(--muted);border-radius:6px;cursor:pointer}\n" +
            "  .pchip:hover{border-color:var(--auth);color:var(--ink)}\n" +
            "  .pchip.shared{color:var(--data);border-color:var(--data)}\n" +
            "  .pchip b{color:var(--ink);margin-left:3px}\n" +
            "  .raw{font-family:ui-monospace,Menlo,monospace;font-size:11px;white-space:pre-wrap;word-break:break-all;max-height:260px;overflow:auto;background:var(--bg);border:1px solid var(--line);border-radius:7px;padding:8px;color:var(--muted);margin-top:6px}\n" +
            "  details summary{cursor:pointer;color:var(--auth);font-size:12px;margin:4px 0;list-style:none}\n" +
            "  details summary::-webkit-details-marker{display:none}\n" +
            "  .empty{color:var(--muted);font-size:12.5px;line-height:1.6}\n" +
            "  .hint{margin-top:12px;color:var(--muted);font-size:12.5px;line-height:1.6}\n" +
            "  .hint code{background:var(--panel);padding:1px 6px;border-radius:5px;font-size:12px;color:var(--ink)}\n" +
            "  #viewer{position:fixed;inset:0;background:rgba(5,8,16,.82);display:none;z-index:20;padding:4vh 4vw}\n" +
            "  #viewer.open{display:block}\n" +
            "  .viewer-card{background:var(--panel-2);border:1px solid var(--line);border-radius:14px;max-width:1180px;margin:0 auto;height:92vh;display:flex;flex-direction:column;overflow:hidden}\n" +
            "  .viewer-head{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:13px 18px;border-bottom:1px solid var(--line)}\n" +
            "  .viewer-head .u{font-family:ui-monospace,Menlo,monospace;font-size:13px;color:var(--ink);word-break:break-all}\n" +
            "  .viewer-body{display:flex;flex-direction:column;flex:1;min-height:0}\n" +
            "  .viewer-col{flex:1;min-height:0;display:flex;flex-direction:column;border-bottom:1px solid var(--line)}\n" +
            "  .viewer-col:last-child{border-bottom:0}\n" +
            "  .viewer-col h5{margin:0;padding:9px 16px;font-size:11px;letter-spacing:.14em;text-transform:uppercase;color:var(--muted);display:flex;justify-content:space-between;align-items:center;gap:10px;border-bottom:1px solid var(--line)}\n" +
            "  .viewer-col h5 .grp{display:flex;gap:6px;align-items:center}\n" +
            "  .viewer-col h5 .mth{color:var(--money);font-weight:700;letter-spacing:.04em}\n" +
            "  .viewer-col pre{margin:0;flex:1;overflow:auto;padding:14px 16px;font-family:ui-monospace,Menlo,monospace;font-size:12px;line-height:1.5;white-space:pre-wrap;word-break:break-word;color:var(--ink)}\n" +
            "  #toast{position:fixed;bottom:26px;left:50%;transform:translateX(-50%) translateY(20px);background:var(--new);color:#0a0e1a;font-weight:600;font-size:13px;padding:9px 18px;border-radius:9px;opacity:0;pointer-events:none;transition:.2s;z-index:30}\n" +
            "  #toast.show{opacity:1;transform:translateX(-50%) translateY(0)}\n" +
            "</style>\n" +
            "\n" +
            "<div class=\"wrap\">\n" +
            "  <header>\n" +
            "    <div>\n" +
            "      <div class=\"eyebrow\">Attack surface &middot; functional map</div>\n" +
            "      <h1><span class=\"dom\" id=\"target\"></span></h1>\n" +
            "    </div>\n" +
            "    <div class=\"meta\">\n" +
            "      <div>generated <b id=\"gen\"></b></div>\n" +
            "      <div><b id=\"total\"></b> endpoints &middot; <span class=\"n\"><b id=\"newcount\"></b> new since last scan</span></div>\n" +
            "    </div>\n" +
            "  </header>\n" +
            "\n" +
            "  <div class=\"bar\">\n" +
            "    <div class=\"seg-group\" role=\"tablist\">\n" +
            "      <button class=\"seg active\" data-view=\"functional\">Functional</button>\n" +
            "      <button class=\"seg\" data-view=\"path\">Raw path</button>\n" +
            "    </div>\n" +
            "    <div class=\"sep\"></div>\n" +
            "    <button class=\"ico\" id=\"zoomout\" title=\"Zoom out\">&minus;</button>\n" +
            "    <button class=\"ico\" id=\"zoomin\" title=\"Zoom in\">+</button>\n" +
            "    <button id=\"fit\">Fit</button>\n" +
            "    <button id=\"collapseall\">Collapse all</button>\n" +
            "    <div class=\"sep\"></div>\n" +
            "    <button id=\"paramsbtn\">Parameters</button>\n" +
            "    <button id=\"png\">Download PNG</button>\n" +
            "    <button id=\"copyallview\">Copy all</button>\n" +
            "    <div class=\"legend\" id=\"legend\"></div>\n" +
            "  </div>\n" +
            "\n" +
            "  <div class=\"stage\">\n" +
            "    <div class=\"map\" id=\"map\"></div>\n" +
            "    <aside class=\"panel\" id=\"panel\"></aside>\n" +
            "  </div>\n" +
            "\n" +
            "  <p class=\"hint\">\n" +
            "    Drag to pan, scroll to zoom, click a branch node (with the <b style=\"color:var(--ink)\">&minus;/+</b> handle) to\n" +
            "    collapse or expand it. Click an endpoint to copy it; click a parameter to light up\n" +
            "    <b style=\"color:var(--data)\">every place it appears</b>. <b style=\"color:var(--new)\">NEW</b> = first seen this scan.\n" +
            "  </p>\n" +
            "</div>\n" +
            "\n" +
            "<script>\n" +
            "/* ---------- the \"thinking\": keyword -> function ---------- */\n" +
            "const CATEGORIES = {\n" +
            "  auth:     {color:\"var(--auth)\",     keywords:[\"login\",\"signin\",\"sign-in\",\"logout\",\"signout\",\"register\",\"signup\",\"sign-up\",\"password\",\"forgot\",\"reset\",\"otp\",\"mfa\",\"2fa\",\"verify\",\"session\",\"oauth\",\"sso\"]},\n" +
            "  account:  {color:\"var(--account)\",  keywords:[\"account\",\"profile\",\"settings\",\"preferences\",\"kyc\",\"identity\",\"me\"]},\n" +
            "  money:    {color:\"var(--money)\",    keywords:[\"transfer\",\"transfers\",\"send\",\"withdraw\",\"deposit\",\"balance\",\"wallet\",\"topup\",\"payout\",\"pix\",\"rails\",\"coins\"]},\n" +
            "  commerce: {color:\"var(--commerce)\", keywords:[\"buy\",\"sell\",\"cart\",\"checkout\",\"order\",\"orders\",\"payment\",\"pay\",\"invoice\",\"billing\",\"subscription\",\"plan\",\"product\"]},\n" +
            "  data:     {color:\"var(--data)\",     keywords:[\"api\",\"mapi\",\"v1\",\"v2\",\"v3\",\"graphql\",\"export\",\"report\",\"download\",\"upload\",\"file\",\"search\",\"query\",\"users\",\"currency\",\"dc\"]},\n" +
            "  admin:    {color:\"var(--admin)\",    keywords:[\"admin\",\"internal\",\"debug\",\"config\",\"manage\",\"dashboard\",\"staff\"]},\n" +
            "};\n" +
            "const ORDER = [\"auth\",\"account\",\"money\",\"commerce\",\"data\",\"admin\"];\n" +
            "const OTHER = {color:\"var(--other)\"};\n" +
            "\n" +
            "\"__SURFACE_MAP_DATA__\"\n" +
            "\n" +
            "const PARAM_COUNTS = {};\n" +
            "ENDPOINTS.forEach(e => (e.params||[]).forEach(p => { PARAM_COUNTS[p] = (PARAM_COUNTS[p]||0)+1; }));\n" +
            "\n" +
            "function classify(path){\n" +
            "  const segs = path.toLowerCase().split(\"/\").filter(Boolean);\n" +
            "  for(const key of ORDER){\n" +
            "    const c = CATEGORIES[key];\n" +
            "    if(segs.some(s => c.keywords.some(k => s === k || s.indexOf(k) > -1))) return key;\n" +
            "  }\n" +
            "  return \"__other\";\n" +
            "}\n" +
            "\n" +
            "/* ---------- build trees ---------- */\n" +
            "function newSeg(name,color){ return {name, kind:\"seg\", color, children:[]}; }\n" +
            "function addPath(parent, ep, color){\n" +
            "  const segs = ep.path.split(\"/\").filter(Boolean);\n" +
            "  let node = parent;\n" +
            "  segs.forEach((seg,i)=>{\n" +
            "    let child = node.children.find(c=>c.name===seg);\n" +
            "    if(!child){\n" +
            "      // depth-0 under root = top-level feature branch → category style\n" +
            "      const kind = (node===parent && parent.kind===\"root\") ? \"category\" : \"seg\";\n" +
            "      child = {name:seg, kind, color, children:[]};\n" +
            "      node.children.push(child);\n" +
            "    }\n" +
            "    node = child;\n" +
            "    if(i===segs.length-1){\n" +
            "      node.kind=\"endpoint\"; node.color=color;\n" +
            "      node.url=\"https://\"+TARGET+ep.path; node.first=ep.first; node.isNew=ep.isNew;\n" +
            "      node.params=ep.params||[]; node.methods=ep.methods||[];\n" +
            "      node.req=ep.req||\"\"; node.resp=ep.resp||\"\";\n" +
            "    }\n" +
            "  });\n" +
            "}\n" +
            "const VERSION_RE = /^(v\\d+|api|mapi)$/i;\n" +
            "\n" +
            "function stripVersionPrefix(path){\n" +
            "  const segs = path.split(\"/\").filter(Boolean);\n" +
            "  if(segs.length > 1 && VERSION_RE.test(segs[0])) return \"/\" + segs.slice(1).join(\"/\");\n" +
            "  return path;\n" +
            "}\n" +
            "\n" +
            "function buildFunctional(){\n" +
            "  const root={name:TARGET, kind:\"root\", children:[]}; const cats={};\n" +
            "  for(const ep of ENDPOINTS){\n" +
            "    const key=classify(ep.path);\n" +
            "    const meta = key===\"__other\"?OTHER:CATEGORIES[key];\n" +
            "    // strip version prefix so first real segment becomes the branch name\n" +
            "    const displayPath = stripVersionPrefix(ep.path);\n" +
            "    // add directly to root, grouped by color key\n" +
            "    // use a virtual per-color subtree rooted at root\n" +
            "    if(!cats[key]) cats[key]={name:\"__group__\"+key, kind:\"root\", color:meta.color, children:[], _virtual:true};\n" +
            "    addPath(cats[key], {...ep, path:displayPath}, meta.color);\n" +
            "  }\n" +
            "  // flatten: push each virtual group's children directly onto root\n" +
            "  ORDER.concat(\"__other\").forEach(k=>{\n" +
            "    if(!cats[k]) return;\n" +
            "    cats[k].children.forEach(c => root.children.push(c));\n" +
            "  });\n" +
            "  return root;\n" +
            "}\n" +
            "function buildPath(){\n" +
            "  const root={name:TARGET, kind:\"root\", children:[]};\n" +
            "  for(const ep of ENDPOINTS) addPath(root, ep, \"var(--muted)\");\n" +
            "  return root;\n" +
            "}\n" +
            "\n" +
            "/* ---------- layout + markup (collapse-aware) ---------- */\n" +
            "const COL=235, ROW=33, PAD=24;\n" +
            "function nodeW(n){\n" +
            "  const extra=n.isNew?5:0; return Math.max(46, Math.min(220, (n.name.length+extra)*7.0+18));\n" +
            "}\n" +
            "function fit(name,w){ const max=Math.floor((w-14)/7.0); return name.length>max ? name.slice(0,Math.max(1,max-1))+\"\\u2026\" : name; }\n" +
            "\n" +
            "const SVG_STYLE = `\n" +
            "  .edge{fill:none;stroke-width:1.4;opacity:.45}\n" +
            "  .node{cursor:pointer}\n" +
            "  .node rect{fill:var(--panel);stroke:var(--accent);stroke-width:1.3;stroke-opacity:.6}\n" +
            "  .node text{fill:var(--ink);font-size:12px;font-family:ui-monospace,Menlo,monospace}\n" +
            "  .node.root rect{fill:#16213f;stroke:var(--ink)}\n" +
            "  .node.category rect{fill:#141d36;stroke-opacity:.9}\n" +
            "  .node.category text{fill:var(--accent);font-weight:600}\n" +
            "  .node.endpoint rect{fill:var(--panel-2)}\n" +
            "  .node.new rect{stroke:var(--new);stroke-width:1.8;stroke-opacity:1;filter:drop-shadow(0 0 5px rgba(255,180,84,.45))}\n" +
            "  .node.new text{fill:var(--new)}\n" +
            "  .node.phl rect{stroke:var(--data);stroke-width:2.4;stroke-opacity:1;filter:drop-shadow(0 0 6px rgba(187,154,247,.6))}\n" +
            "  .badge rect{fill:var(--new)}\n" +
            "  .badge-t{fill:#0a0e1a;font-size:9px;font-weight:700;letter-spacing:.08em;font-family:ui-monospace,Menlo,monospace}\n" +
            "  .tgl circle{fill:var(--panel-2);stroke:var(--accent);stroke-width:1.3}\n" +
            "  .tgl.collapsed circle{fill:var(--accent)}\n" +
            "  .tgl-t{fill:var(--muted);font-size:11px;font-family:ui-monospace,Menlo,monospace;pointer-events:none}\n" +
            "  .tgl.collapsed .tgl-t{fill:#0a0e1a;font-weight:700}\n" +
            "`;\n" +
            "\n" +
            "function buildMarkup(root){\n" +
            "  let leaf=0, maxDepth=0;\n" +
            "  (function L(n,d){\n" +
            "    n.depth=d; maxDepth=Math.max(maxDepth,d);\n" +
            "    const kids=n.collapsed?[]:n.children;\n" +
            "    if(!kids.length){ n.y=leaf*ROW+ROW/2+PAD; leaf++; }\n" +
            "    else { kids.forEach(c=>L(c,d+1)); n.y=(kids[0].y+kids[kids.length-1].y)/2; }\n" +
            "    n.x=d*COL+PAD;\n" +
            "  })(root,0);\n" +
            "  const dim={w:maxDepth*COL+PAD+230, h:Math.max(leaf,1)*ROW+PAD*2};\n" +
            "  const edges=[], nodes=[];\n" +
            "  (function W(n){\n" +
            "    nodes.push(n);\n" +
            "    const kids=n.collapsed?[]:n.children;\n" +
            "    for(const c of kids){\n" +
            "      edges.push(`<path class=\"edge\" d=\"M${n.x+nodeW(n)},${n.y} C${n.x+nodeW(n)+40},${n.y} ${c.x-40},${c.y} ${c.x},${c.y}\" style=\"stroke:${c.color||'var(--line)'}\"/>`);\n" +
            "      W(c);\n" +
            "    }\n" +
            "  })(root);\n" +
            "  const boxes = nodes.map((n,i)=>{\n" +
            "    const w=nodeW(n), h=22, x=n.x, y=n.y-h/2;\n" +
            "    const cls=\"node \"+n.kind+(n.isNew?\" new\":\"\");\n" +
            "    const title=n.url?`<title>${n.url}\\nfirst seen ${n.first}</title>`:\"\";\n" +
            "    let extra=\"\";\n" +
            "    if(n.isNew){ const px=x+w+8; extra=`<g class=\"badge\"><rect x=\"${px}\" y=\"${y+3}\" rx=\"4\" width=\"34\" height=\"16\"></rect><text x=\"${px+17}\" y=\"${y+14}\" text-anchor=\"middle\" class=\"badge-t\">NEW</text></g>`; }\n" +
            "    let tgl=\"\";\n" +
            "    if(n.children && n.children.length){\n" +
            "      const tx=x-15, ty=n.y;\n" +
            "      tgl=`<g class=\"tgl${n.collapsed?\" collapsed\":\"\"}\"><circle cx=\"${tx}\" cy=\"${ty}\" r=\"6.5\"></circle><text x=\"${tx}\" y=\"${ty+3.5}\" text-anchor=\"middle\" class=\"tgl-t\">${n.collapsed?\"+\":\"\\u2212\"}</text></g>`;\n" +
            "    }\n" +
            "    const accent=n.kind===\"root\"?\"var(--ink)\":(n.color||\"var(--muted)\");\n" +
            "    return `<g class=\"${cls}\" data-id=\"${i}\" style=\"--accent:${accent}\">${title}${tgl}<rect x=\"${x}\" y=\"${y}\" rx=\"6\" width=\"${w}\" height=\"${h}\"></rect><text x=\"${x+9}\" y=\"${y+15}\">${fit(n.name,w)}</text>${extra}</g>`;\n" +
            "  }).join(\"\");\n" +
            "  return {inner:edges.join(\"\")+boxes, dim, nodes};\n" +
            "}\n" +
            "\n" +
            "/* ---------- render + view ---------- */\n" +
            "let RENDER_NODES=[], CURRENT_ROOT=null, LAST_INNER=\"\", LAST_DIM={w:1,h:1}, COLLAPSED_ALL=false;\n" +
            "let VIEWER_NODE=null;\n" +
            "const VIEW={k:1,x:0,y:0};\n" +
            "function applyView(){ const vp=document.getElementById(\"viewport\"); if(vp) vp.setAttribute(\"transform\",`translate(${VIEW.x},${VIEW.y}) scale(${VIEW.k})`); }\n" +
            "function fitView(){\n" +
            "  const m=document.getElementById(\"map\"); const VW=m.clientWidth, VH=m.clientHeight;\n" +
            "  const k=Math.max(0.15, Math.min(1.6, Math.min(VW/LAST_DIM.w, VH/LAST_DIM.h)*0.96));\n" +
            "  VIEW.k=k; VIEW.x=Math.max(12,(VW-LAST_DIM.w*k)/2); VIEW.y=(VH-LAST_DIM.h*k)/2; applyView();\n" +
            "}\n" +
            "function zoomAt(f,cx,cy){\n" +
            "  const k2=Math.max(0.12, Math.min(3.5, VIEW.k*f));\n" +
            "  VIEW.x = cx-(cx-VIEW.x)*(k2/VIEW.k); VIEW.y = cy-(cy-VIEW.y)*(k2/VIEW.k); VIEW.k=k2; applyView();\n" +
            "}\n" +
            "function render(root, doFit){\n" +
            "  const built=buildMarkup(root);\n" +
            "  RENDER_NODES=built.nodes; LAST_INNER=built.inner; LAST_DIM=built.dim;\n" +
            "  const m=document.getElementById(\"map\");\n" +
            "  const VW=Math.max(320,m.clientWidth), VH=Math.max(320,m.clientHeight);\n" +
            "  m.innerHTML=`<svg id=\"surface\" width=\"${VW}\" height=\"${VH}\" viewBox=\"0 0 ${VW} ${VH}\" xmlns=\"http://www.w3.org/2000/svg\"><style>${SVG_STYLE}</style><rect x=\"0\" y=\"0\" width=\"${VW}\" height=\"${VH}\" fill=\"var(--bg)\"/><g id=\"viewport\">${built.inner}</g></svg>`;\n" +
            "  if(doFit) fitView(); else applyView();\n" +
            "}\n" +
            "\n" +
            "/* ---------- chrome ---------- */\n" +
            "function drawLegend(){\n" +
            "  document.getElementById(\"legend\").innerHTML =\n" +
            "    ORDER.map(k=>`<span><i class=\"dot\" style=\"background:${CATEGORIES[k].color}\"></i></span>`).join(\"\")\n" +
            "    + `<span><i class=\"dot\" style=\"background:var(--new)\"></i>new</span>`\n" +
            "    + `<span><i class=\"dot\" style=\"background:var(--data)\"></i>param hit</span>`;\n" +
            "}\n" +
            "function refresh(view){\n" +
            "  COLLAPSED_ALL=false; document.getElementById(\"collapseall\").textContent=\"Collapse all\";\n" +
            "  CURRENT_ROOT = view===\"path\"?buildPath():buildFunctional();\n" +
            "  render(CURRENT_ROOT, true);\n" +
            "}\n" +
            "document.querySelectorAll(\".seg\").forEach(b=>b.onclick=()=>{\n" +
            "  document.querySelectorAll(\".seg\").forEach(x=>x.classList.remove(\"active\"));\n" +
            "  b.classList.add(\"active\"); refresh(b.dataset.view);\n" +
            "});\n" +
            "document.getElementById(\"zoomin\").onclick=()=>{ const m=document.getElementById(\"map\"); zoomAt(1.25, m.clientWidth/2, m.clientHeight/2); };\n" +
            "document.getElementById(\"zoomout\").onclick=()=>{ const m=document.getElementById(\"map\"); zoomAt(1/1.25, m.clientWidth/2, m.clientHeight/2); };\n" +
            "document.getElementById(\"fit\").onclick=fitView;\n" +
            "document.getElementById(\"collapseall\").onclick=function(){\n" +
            "  COLLAPSED_ALL=!COLLAPSED_ALL; this.textContent=COLLAPSED_ALL?\"Expand all\":\"Collapse all\";\n" +
            "  (function w(n){ if(n!==CURRENT_ROOT && n.children && n.children.length) n.collapsed=COLLAPSED_ALL; n.children.forEach(w); })(CURRENT_ROOT);\n" +
            "  render(CURRENT_ROOT, true);\n" +
            "};\n" +
            "\n" +
            "/* ---------- inspect & copy ---------- */\n" +
            "function endpointsUnder(node){ const o=[]; (function w(n){ if(n.url)o.push(n); n.children.forEach(w); })(node); return o; }\n" +
            "function esc(s){ return (\"\"+s).replace(/&/g,\"&amp;\").replace(/</g,\"&lt;\").replace(/>/g,\"&gt;\").replace(/\"/g,\"&quot;\"); }\n" +
            "function toast(m){ const t=document.getElementById(\"toast\"); t.textContent=m; t.classList.add(\"show\"); clearTimeout(t._t); t._t=setTimeout(()=>t.classList.remove(\"show\"),1500); }\n" +
            "function fallbackCopy(text){\n" +
            "  const ta=document.createElement(\"textarea\"); ta.value=text; ta.style.position=\"fixed\"; ta.style.opacity=\"0\";\n" +
            "  document.body.appendChild(ta); ta.focus(); ta.select(); let ok=false;\n" +
            "  try{ ok=document.execCommand(\"copy\"); }catch(e){}\n" +
            "  document.body.removeChild(ta); toast(ok?\"Copied\":\"Copy failed\");\n" +
            "}\n" +
            "function copyText(text){\n" +
            "  if(navigator.clipboard && window.isSecureContext) navigator.clipboard.writeText(text).then(()=>toast(\"Copied\"),()=>fallbackCopy(text));\n" +
            "  else fallbackCopy(text);\n" +
            "}\n" +
            "function paramChips(params){\n" +
            "  if(!params || !params.length) return `<div class=\"muted\">none captured</div>`;\n" +
            "  return `<div class=\"pwrap\">`+params.map(p=>{\n" +
            "    const shared = PARAM_COUNTS[p]>1;\n" +
            "    return `<button class=\"pchip${shared?\" shared\":\"\"}\" data-param=\"${esc(p)}\">${esc(p)}${shared?`<b>${PARAM_COUNTS[p]}</b>`:\"\"}</button>`;\n" +
            "  }).join(\"\")+`</div>`;\n" +
            "}\n" +
            "function wireParamChips(p){ p.querySelectorAll(\"[data-param]\").forEach(b=>b.onclick=ev=>{ev.stopPropagation(); highlightParam(b.getAttribute(\"data-param\"));}); }\n" +
            "function showPanel(node){\n" +
            "  const eps=endpointsUnder(node);\n" +
            "  let h=`<div class=\"kind\">${esc(node.kind)}${node.children&&node.children.length?(node.collapsed?\" \\u00b7 collapsed\":\" \\u00b7 expanded\"):\"\"}</div><div class=\"ttl\">${esc(node.name)}${node.kind===\"category\"?` <span style=\"color:var(--muted);font-size:12px;font-weight:400\">\\u2014 ${eps.length} endpoint${eps.length===1?\"\":\"s\"}</span>`:\"\"}</div>`;\n" +
            "  if(node.url){\n" +
            "    const methods=(node.methods&&node.methods.length)?node.methods.join(\", \"):\"unknown\";\n" +
            "    h+=`<div class=\"url\">${esc(node.url)}</div>`+\n" +
            "       `<div class=\"row\"><button class=\"icp\" data-copy=\"${esc(node.url)}\">Copy URL</button>`+\n" +
            "       (node.isNew?`<span class=\"new-tag\">NEW</span>`:``)+`</div>`+\n" +
            "       `<div class=\"muted\">methods: <b style=\"color:var(--money)\">${esc(methods)}</b></div>`+\n" +
            "       `<div class=\"muted\">first seen ${esc(node.first||\"\\u2014\")}</div>`+\n" +
            "       `<h4>Parameters</h4>${paramChips(node.params)}`+\n" +
            "       `<h4>Raw HTTP</h4><div class=\"row\"><button class=\"icp\" id=\"viewhttp\">View request &amp; response</button>`+\n" +
            "       (node.req?`<button class=\"icp\" id=\"copyreq\">Copy request</button>`:``)+\n" +
            "       (node.resp?`<button class=\"icp\" id=\"copyresp\">Copy response</button>`:``)+`</div>`+\n" +
            "       (!node.req&&!node.resp?`<div class=\"muted\">no raw HTTP stored \\u2014 re-capture with the latest extension</div>`:``);\n" +
            "  }\n" +
            "  h+=`<h4>Endpoints in branch (${eps.length})</h4>`;\n" +
            "  if(eps.length){\n" +
            "    h+=`<div class=\"row\"><button class=\"icp\" id=\"copyall\">Copy all ${eps.length}</button></div><div class=\"eplist\">`+\n" +
            "       eps.map(e=>`<div class=\"epitem${e.isNew?\" new\":\"\"}\"><span>${esc(e.url.replace(\"https://\"+TARGET,\"\")||\"/\")}</span><button class=\"icp\" data-copy=\"${esc(e.url)}\">copy</button></div>`).join(\"\")+`</div>`;\n" +
            "  } else { h+=`<div class=\"empty\">No endpoints under this node.</div>`; }\n" +
            "  const p=document.getElementById(\"panel\"); p.innerHTML=h;\n" +
            "  p.querySelectorAll(\"[data-copy]\").forEach(b=>b.onclick=ev=>{ev.stopPropagation(); copyText(b.getAttribute(\"data-copy\"));});\n" +
            "  const all=p.querySelector(\"#copyall\"); if(all) all.onclick=()=>copyText(eps.map(e=>e.url).join(\"\\n\"));\n" +
            "  const cq=p.querySelector(\"#copyreq\"); if(cq) cq.onclick=()=>copyText(node.req);\n" +
            "  const cr=p.querySelector(\"#copyresp\"); if(cr) cr.onclick=()=>copyText(node.resp);\n" +
            "  const vh=p.querySelector(\"#viewhttp\"); if(vh) vh.onclick=()=>openViewer(node);\n" +
            "  wireParamChips(p);\n" +
            "}\n" +
            "\n" +
            "/* ---------- parameter analysis ---------- */\n" +
            "function clearParamHL(){ document.querySelectorAll(\"#surface g.phl\").forEach(g=>g.classList.remove(\"phl\")); }\n" +
            "function highlightParam(p){\n" +
            "  clearParamHL();\n" +
            "  const hits=[];\n" +
            "  RENDER_NODES.forEach((n,i)=>{\n" +
            "    if(n.params && n.params.indexOf(p)>-1){\n" +
            "      const g=document.querySelector(`#surface g[data-id=\"${i}\"]`);\n" +
            "      if(g){ g.classList.add(\"phl\"); hits.push(n); }\n" +
            "    }\n" +
            "  });\n" +
            "  toast(`${p}: ${hits.length} visible place${hits.length===1?\"\":\"s\"}`);\n" +
            "  const panel=document.getElementById(\"panel\");\n" +
            "  panel.innerHTML=`<div class=\"kind\">parameter</div><div class=\"ttl\" style=\"color:var(--data)\">${esc(p)}</div>`+\n" +
            "    `<div class=\"muted\">used in ${PARAM_COUNTS[p]||0} place${(PARAM_COUNTS[p]||0)===1?\"\":\"s\"} (collapse may hide some)</div>`+\n" +
            "    `<div class=\"row\"><button class=\"icp\" id=\"pcopyall\">Copy all ${hits.length}</button><button class=\"icp\" id=\"pclear\">Clear</button></div>`+\n" +
            "    `<div class=\"eplist\">`+hits.map(e=>`<div class=\"epitem${e.isNew?\" new\":\"\"}\"><span>${esc(e.url.replace(\"https://\"+TARGET,\"\")||\"/\")}</span><button class=\"icp\" data-copy=\"${esc(e.url)}\">copy</button></div>`).join(\"\")+`</div>`;\n" +
            "  panel.querySelectorAll(\"[data-copy]\").forEach(b=>b.onclick=ev=>{ev.stopPropagation(); copyText(b.getAttribute(\"data-copy\"));});\n" +
            "  panel.querySelector(\"#pcopyall\").onclick=()=>copyText(hits.map(e=>e.url).join(\"\\n\"));\n" +
            "  panel.querySelector(\"#pclear\").onclick=()=>{ clearParamHL(); showAllParams(); };\n" +
            "}\n" +
            "function showAllParams(){\n" +
            "  const names=Object.keys(PARAM_COUNTS).sort((a,b)=> (PARAM_COUNTS[b]-PARAM_COUNTS[a]) || a.localeCompare(b));\n" +
            "  const panel=document.getElementById(\"panel\");\n" +
            "  panel.innerHTML=`<div class=\"kind\">parameters</div><div class=\"ttl\">Across the app (${names.length})</div>`+\n" +
            "    `<div class=\"muted\">shared params (used in more than one place) are highlighted \\u2014 click any to map it</div>`+\n" +
            "    `<h4>All parameters</h4>${paramChips(names)}`;\n" +
            "  wireParamChips(panel);\n" +
            "}\n" +
            "\n" +
            "/* ---------- request / response viewer ---------- */\n" +
            "function openViewer(node){\n" +
            "  VIEWER_NODE=node;\n" +
            "  document.querySelector(\"#viewer .u\").textContent=node.url||node.name||\"\";\n" +
            "  document.getElementById(\"vmth\").textContent=(node.methods&&node.methods.length)?node.methods.join(\" \"):\"\";\n" +
            "  document.getElementById(\"vreq\").textContent=node.req||\"(no request captured for this endpoint)\";\n" +
            "  document.getElementById(\"vresp\").textContent=node.resp||\"(no response captured for this endpoint)\";\n" +
            "  document.getElementById(\"viewer\").classList.add(\"open\");\n" +
            "}\n" +
            "function closeViewer(){ document.getElementById(\"viewer\").classList.remove(\"open\"); }\n" +
            "\n" +
            "/* ---------- pan / zoom / click ---------- */\n" +
            "function onNodeClick(n){\n" +
            "  if(!n) return; clearParamHL();\n" +
            "  if(n.children && n.children.length){ n.collapsed=!n.collapsed; render(CURRENT_ROOT,false); showPanel(n); }\n" +
            "  else { if(n.url) copyText(n.url); showPanel(n); }\n" +
            "}\n" +
            "(function(){\n" +
            "  const m=document.getElementById(\"map\"); let drag=null;\n" +
            "  m.addEventListener(\"mousedown\", e=>{\n" +
            "    if(e.button!==0) return;\n" +
            "    drag={mx:e.clientX,my:e.clientY,vx:VIEW.x,vy:VIEW.y,moved:false,node:e.target.closest(\"g.node\")};\n" +
            "  });\n" +
            "  window.addEventListener(\"mousemove\", e=>{\n" +
            "    if(!drag) return;\n" +
            "    const dx=e.clientX-drag.mx, dy=e.clientY-drag.my;\n" +
            "    if(!drag.moved && Math.hypot(dx,dy)>4){ drag.moved=true; m.classList.add(\"grabbing\"); }\n" +
            "    if(drag.moved){ VIEW.x=drag.vx+dx; VIEW.y=drag.vy+dy; applyView(); }\n" +
            "  });\n" +
            "  window.addEventListener(\"mouseup\", ()=>{\n" +
            "    if(!drag) return; const d=drag; drag=null; m.classList.remove(\"grabbing\");\n" +
            "    if(!d.moved){ if(d.node) onNodeClick(RENDER_NODES[+d.node.getAttribute(\"data-id\")]); else clearParamHL(); }\n" +
            "  });\n" +
            "  m.addEventListener(\"wheel\", e=>{\n" +
            "    e.preventDefault();\n" +
            "    const r=m.getBoundingClientRect();\n" +
            "    zoomAt(e.deltaY<0?1.12:1/1.12, e.clientX-r.left, e.clientY-r.top);\n" +
            "  }, {passive:false});\n" +
            "  let rz; window.addEventListener(\"resize\", ()=>{ clearTimeout(rz); rz=setTimeout(()=>{ if(CURRENT_ROOT) render(CURRENT_ROOT,false); },150); });\n" +
            "})();\n" +
            "\n" +
            "document.getElementById(\"copyallview\").onclick=()=>{\n" +
            "  const eps=CURRENT_ROOT?endpointsUnder(CURRENT_ROOT):[];\n" +
            "  if(eps.length) copyText(eps.map(e=>e.url).join(\"\\n\")); else toast(\"Nothing to copy\");\n" +
            "};\n" +
            "document.getElementById(\"paramsbtn\").onclick=showAllParams;\n" +
            "\n" +
            "/* ---------- PNG export (full diagram, not just the visible viewport) ---------- */\n" +
            "document.getElementById(\"png\").onclick=()=>{\n" +
            "  let str=`<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${LAST_DIM.w}\" height=\"${LAST_DIM.h}\" viewBox=\"0 0 ${LAST_DIM.w} ${LAST_DIM.h}\"><style>${SVG_STYLE}</style><rect x=\"0\" y=\"0\" width=\"${LAST_DIM.w}\" height=\"${LAST_DIM.h}\" fill=\"var(--bg)\"/>${LAST_INNER}</svg>`;\n" +
            "  const css=getComputedStyle(document.documentElement);\n" +
            "  [\"bg\",\"panel\",\"panel-2\",\"ink\",\"muted\",\"line\",\"new\",\"auth\",\"account\",\"money\",\"commerce\",\"data\",\"admin\",\"other\"]\n" +
            "    .forEach(v=>{ str=str.split(`var(--${v})`).join(css.getPropertyValue(\"--\"+v).trim()); });\n" +
            "  const url=URL.createObjectURL(new Blob([str],{type:\"image/svg+xml;charset=utf-8\"}));\n" +
            "  const img=new Image();\n" +
            "  img.onload=()=>{\n" +
            "    const s=2, c=document.createElement(\"canvas\");\n" +
            "    c.width=LAST_DIM.w*s; c.height=LAST_DIM.h*s;\n" +
            "    const ctx=c.getContext(\"2d\"); ctx.scale(s,s); ctx.drawImage(img,0,0); URL.revokeObjectURL(url);\n" +
            "    c.toBlob(b=>{ const a=document.createElement(\"a\"); a.href=URL.createObjectURL(b); a.download=\"surface-map-\"+GENERATED.replace(/[^0-9]/g,\"\")+\".png\"; a.click(); });\n" +
            "  };\n" +
            "  img.src=url;\n" +
            "};\n" +
            "\n" +
            "/* ---------- init ---------- */\n" +
            "document.body.insertAdjacentHTML(\"beforeend\",'<div id=\"toast\"></div>');\n" +
            "document.body.insertAdjacentHTML(\"beforeend\",\n" +
            "  '<div id=\"viewer\"><div class=\"viewer-card\">'+\n" +
            "    '<div class=\"viewer-head\"><span class=\"u\"></span><button class=\"icp\" id=\"vclose\">Close</button></div>'+\n" +
            "    '<div class=\"viewer-body\">'+\n" +
            "      '<div class=\"viewer-col\"><h5><span class=\"grp\">Request <span class=\"mth\" id=\"vmth\"></span></span>'+\n" +
            "        '<span class=\"grp\"><button class=\"icp\" data-scroll=\"vreq\" data-to=\"top\">&uarr; top</button><button class=\"icp\" data-scroll=\"vreq\" data-to=\"bottom\">&darr; bottom</button><button class=\"icp\" id=\"vcopyreq\">copy</button></span></h5>'+\n" +
            "        '<pre id=\"vreq\"></pre></div>'+\n" +
            "      '<div class=\"viewer-col\"><h5><span class=\"grp\">Response</span>'+\n" +
            "        '<span class=\"grp\"><button class=\"icp\" data-scroll=\"vresp\" data-to=\"top\">&uarr; top</button><button class=\"icp\" data-scroll=\"vresp\" data-to=\"bottom\">&darr; bottom</button><button class=\"icp\" id=\"vcopyresp\">copy</button></span></h5>'+\n" +
            "        '<pre id=\"vresp\"></pre></div>'+\n" +
            "  '</div></div></div>');\n" +
            "document.getElementById(\"vclose\").onclick=closeViewer;\n" +
            "document.getElementById(\"viewer\").addEventListener(\"click\",e=>{ if(e.target.id===\"viewer\") closeViewer(); });\n" +
            "document.getElementById(\"vcopyreq\").onclick=()=>copyText(VIEWER_NODE?VIEWER_NODE.req:\"\");\n" +
            "document.getElementById(\"vcopyresp\").onclick=()=>copyText(VIEWER_NODE?VIEWER_NODE.resp:\"\");\n" +
            "document.querySelectorAll(\"#viewer [data-scroll]\").forEach(b=>b.onclick=()=>{\n" +
            "  const pre=document.getElementById(b.getAttribute(\"data-scroll\"));\n" +
            "  pre.scrollTo({top: b.getAttribute(\"data-to\")===\"bottom\"?pre.scrollHeight:0, behavior:\"smooth\"});\n" +
            "});\n" +
            "window.addEventListener(\"keydown\",e=>{ if(e.key===\"Escape\") closeViewer(); });\n" +
            "document.getElementById(\"target\").textContent=TARGET;\n" +
            "document.getElementById(\"gen\").textContent=GENERATED;\n" +
            "document.getElementById(\"total\").textContent=ENDPOINTS.length;\n" +
            "document.getElementById(\"newcount\").textContent=ENDPOINTS.filter(e=>e.isNew).length;\n" +
            "document.getElementById(\"panel\").innerHTML='<div class=\"empty\">Drag to pan, scroll to zoom. Click a branch handle to collapse it, an endpoint to copy it, or the <b style=\"color:var(--ink)\">Parameters</b> button to map shared params.</div>';\n" +
            "drawLegend();\n" +
            "refresh(\"functional\");\n" +
            "</script>\n" +
            "";

    public static String render(SurfaceMapModel model) {
        List<EndpointEntry> eps = model.snapshot();
        String target    = model.dominantHost();
        String generated = LocalDateTime.now().format(FMT);

        StringBuilder data = new StringBuilder();
        data.append("const TARGET = ").append(jsonStr(target)).append(";\n");
        data.append("const GENERATED = ").append(jsonStr(generated)).append(";\n");
        data.append("const ENDPOINTS = [\n");
        for (int i = 0; i < eps.size(); i++) {
            EndpointEntry e = eps.get(i);
            data.append("  {");
            data.append("\"path\":").append(jsonStr(e.path)).append(',');
            data.append("\"first\":").append(jsonStr(e.firstSeen)).append(',');
            data.append("\"isNew\":").append(e.isNew).append(',');
            data.append("\"params\":").append(jsonStrArr(e.params)).append(',');
            data.append("\"methods\":").append(jsonStrArr(e.methods)).append(',');
            data.append("\"req\":").append(jsonStr(e.request)).append(',');
            data.append("\"resp\":").append(jsonStr(e.response));
            data.append('}');
            if (i < eps.size() - 1) data.append(',');
            data.append('\n');
        }
        data.append("];");

        return TEMPLATE.replace("\"__SURFACE_MAP_DATA__\"", data.toString());
    }

    // --- tiny JSON helpers ---------------------------------------------------

    private static String jsonStr(String s) {
        if (s == null) return "\"\"";
        String escaped = s.replace("\\", "\\\\")
                          .replace("\"", "\\\"")
                          .replace("\r", "\\r")
                          .replace("\n", "\\n")
                          .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    private static String jsonStrArr(Collection<String> items) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        List<String> sorted = new ArrayList<>(items);
        Collections.sort(sorted);
        for (String s : sorted) {
            if (!first) sb.append(',');
            first = false;
            sb.append(jsonStr(s));
        }
        sb.append(']');
        return sb.toString();
    }
}
