// Sinh avatar-lib.vN.json từ các mảnh SVG bên dưới (nguồn: bản demo "Avatar 50 con vật").
// Chạy: node libs/avatar-lib/build-lib.mjs
// QUY TẮC: một phiên bản đã phát hành KHÔNG được đổi nghĩa (cùng avatar_key phải ra cùng hình).
// Muốn thêm/sửa: tăng VERSION, chỉ THÊM khoá mới, không xoá/đổi thứ tự bảng màu.
import { writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const VERSION = 1;
const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, "..", "..");

const L = '<circle cx="26" cy="30" r="12" fill="{{fur}}"/><circle cx="74" cy="30" r="12" fill="{{fur}}"/>';
const E = {
  none: "",
  tri: '<polygon points="24,44 26,14 46,30" fill="{{fur}}"/><polygon points="76,44 74,14 54,30" fill="{{fur}}"/><polygon points="29,36 29,21 41,30" fill="{{accent}}"/><polygon points="71,36 71,21 59,30" fill="{{accent}}"/>',
  rnd: L + '<circle cx="26" cy="30" r="6" fill="{{accent}}"/><circle cx="74" cy="30" r="6" fill="{{accent}}"/>',
  big: '<circle cx="20" cy="36" r="16" fill="{{fur}}"/><circle cx="80" cy="36" r="16" fill="{{fur}}"/><circle cx="20" cy="36" r="9" fill="{{accent}}"/><circle cx="80" cy="36" r="9" fill="{{accent}}"/>',
  long: '<ellipse cx="36" cy="22" rx="9" ry="24" fill="{{fur}}"/><ellipse cx="64" cy="22" rx="9" ry="24" fill="{{fur}}"/><ellipse cx="36" cy="22" rx="4.5" ry="17" fill="{{accent}}"/><ellipse cx="64" cy="22" rx="4.5" ry="17" fill="{{accent}}"/>',
  flop: '<ellipse cx="20" cy="56" rx="10" ry="22" fill="{{accent}}" transform="rotate(12 20 56)"/><ellipse cx="80" cy="56" rx="10" ry="22" fill="{{accent}}" transform="rotate(-12 80 56)"/>',
};
const H = {
  round: '<circle cx="50" cy="56" r="30" fill="{{fur}}"/>',
  wide: '<ellipse cx="50" cy="58" rx="34" ry="28" fill="{{fur}}"/>',
  tall: '<ellipse cx="50" cy="54" rx="26" ry="33" fill="{{fur}}"/>',
};
const MU = {
  m: '<ellipse cx="50" cy="67" rx="14" ry="10" fill="{{belly}}"/>',
  w: '<ellipse cx="50" cy="68" rx="20" ry="12" fill="{{belly}}"/>',
  s: '<ellipse cx="50" cy="66" rx="10" ry="7" fill="{{belly}}"/>',
  f: '<ellipse cx="50" cy="60" rx="22" ry="20" fill="{{belly}}"/>',
  n: "",
};
const EY = {
  n: '<circle cx="39" cy="52" r="4" fill="#222"/><circle cx="61" cy="52" r="4" fill="#222"/><circle cx="40.2" cy="50.8" r="1.3" fill="#fff"/><circle cx="62.2" cy="50.8" r="1.3" fill="#fff"/>',
  big: '<circle cx="37" cy="50" r="10" fill="#fff"/><circle cx="63" cy="50" r="10" fill="#fff"/><circle cx="37" cy="50" r="5" fill="#222"/><circle cx="63" cy="50" r="5" fill="#222"/>',
  top: '<circle cx="32" cy="32" r="11" fill="{{fur}}"/><circle cx="68" cy="32" r="11" fill="{{fur}}"/><circle cx="32" cy="32" r="7" fill="#fff"/><circle cx="68" cy="32" r="7" fill="#fff"/><circle cx="32" cy="32" r="3.5" fill="#222"/><circle cx="68" cy="32" r="3.5" fill="#222"/>',
};
const NO = {
  none: "",
  dot: '<ellipse cx="50" cy="62" rx="5" ry="3.5" fill="#222"/><path d="M50 65v4M45 70q5 4 5-1q0 5 5 1" stroke="#222" stroke-width="1.4" fill="none"/>',
  tri: '<polygon points="46,60 54,60 50,65" fill="{{accent}}"/><path d="M50 65v4M44 70q6 4 6-1q0 5 6 1" stroke="#222" stroke-width="1.5" fill="none"/>',
  pig: '<ellipse cx="45" cy="67" rx="2.3" ry="3.2" fill="{{accent}}"/><ellipse cx="55" cy="67" rx="2.3" ry="3.2" fill="{{accent}}"/>',
  beak: '<polygon points="43,58 57,58 50,72" fill="#F2A33A"/>',
  bill: '<ellipse cx="50" cy="68" rx="14" ry="7" fill="#F2A33A"/>',
  smile: '<path d="M34 68q16 12 32 0" stroke="#222" stroke-width="2" fill="none" stroke-linecap="round"/>',
};
// X: [lớp, svg]  B = vẽ trước đầu, M = trên đầu, F = trên cùng
const X = {
  mane: ["B", '<circle cx="50" cy="56" r="43" fill="{{accent}}"/>'],
  shell: ["B", '<ellipse cx="50" cy="72" rx="42" ry="26" fill="{{accent}}"/>'],
  tail: ["B", '<path d="M78 84q24-6 10-42" stroke="{{accent}}" stroke-width="12" fill="none" stroke-linecap="round"/>'],
  antler: ["B", '<path d="M34 34l-8-22m4 12l-10-2M66 34l8-22m-4 12l10-2" stroke="#8a5a2b" stroke-width="3.5" fill="none" stroke-linecap="round"/>'],
  horn: ["B", '<polygon points="32,34 30,12 44,28" fill="#F1E9D8"/><polygon points="68,34 70,12 56,28" fill="#F1E9D8"/>'],
  wool: ["B", '<g fill="{{belly}}"><circle cx="28" cy="38" r="13"/><circle cx="50" cy="28" r="14"/><circle cx="72" cy="38" r="13"/><circle cx="22" cy="58" r="11"/><circle cx="78" cy="58" r="11"/></g>'],
  spikes: ["B", '<polygon points="14,60 18,30 30,40 34,16 46,34 50,10 54,34 66,16 70,40 82,30 86,60" fill="{{accent}}"/>'],
  wing: ["B", '<path d="M20 50q-22-4-16 24q10-10 18-6zM80 50q22-4 16 24q-10-10-18-6z" fill="{{accent}}"/>'],
  uhorn: ["B", '<polygon points="44,32 50,2 56,32" fill="#F2C14E"/>'],
  knob: ["B", '<path d="M38 28v-10M62 28v-10" stroke="#8a5a2b" stroke-width="3"/><circle cx="38" cy="16" r="4" fill="{{accent}}"/><circle cx="62" cy="16" r="4" fill="{{accent}}"/>'],
  mohawk: ["B", '<path d="M38 30Q36 10 50 8Q64 10 62 30z" fill="{{accent}}"/>'],
  tuft: ["B", '<path d="M44 28Q42 14 50 12Q58 14 56 28z" fill="{{accent}}"/>'],
  crest: ["B", '<path d="M40 30L36 8l10 12 4-16 4 16 10-12-4 22z" fill="{{accent}}"/>'],
  comb: ["B", '<circle cx="42" cy="28" r="6" fill="#E3453F"/><circle cx="50" cy="24" r="7" fill="#E3453F"/><circle cx="58" cy="28" r="6" fill="#E3453F"/>'],
  stripe: ["M", '<path d="M50 27v11M40 29l3 9M60 29l-3 9M22 56h9M23 64l8-2M78 56h-9M77 64l-8-2" stroke="{{accent}}" stroke-width="3" stroke-linecap="round"/>'],
  spots: ["M", '<g fill="{{accent}}"><circle cx="30" cy="42" r="5"/><circle cx="68" cy="36" r="6"/><circle cx="72" cy="62" r="4"/><circle cx="52" cy="34" r="3"/></g>'],
  mask: ["M", '<path d="M22 50q28-12 56 0q-4 12-18 6q-10-4-20 0q-14 6-18-6z" fill="{{accent}}"/>'],
  patch: ["M", '<ellipse cx="38" cy="52" rx="8" ry="10" fill="{{accent}}" transform="rotate(25 38 52)"/><ellipse cx="62" cy="52" rx="8" ry="10" fill="{{accent}}" transform="rotate(-25 62 52)"/>'],
  cheek: ["M", '<circle cx="30" cy="64" r="6" fill="#FF7B8A" opacity=".45"/><circle cx="70" cy="64" r="6" fill="#FF7B8A" opacity=".45"/>'],
  whisk: ["F", '<path d="M30 62l-14-3M30 66l-14 3M70 62l14-3M70 66l14 3" stroke="#222" stroke-width="1.2"/>'],
  tongue: ["F", '<ellipse cx="50" cy="75" rx="4" ry="5" fill="#FF7B8A"/>'],
  tusk: ["F", '<path d="M38 70q-4 10 2 14M62 70q4 10-2 14" stroke="#F1E9D8" stroke-width="4" fill="none" stroke-linecap="round"/>'],
  trunk: ["F", '<path d="M50 56v26q0 8 9 6" stroke="{{fur}}" stroke-width="12" fill="none" stroke-linecap="round"/>'],
  teeth: ["F", '<rect x="46" y="70" width="8" height="8" rx="1.5" fill="#fff" stroke="#222"/>'],
  fang: ["F", '<path d="M44 71l2 6 2-6M52 71l2 6 2-6" fill="#fff"/>'],
  beard: ["F", '<path d="M44 76q6 14 12 0z" fill="{{belly}}"/>'],
  rhorn: ["F", '<polygon points="43,66 50,44 57,66" fill="#D9D2C5"/>'],
};
// khoá|tên|tai|đầu|mõm|mũi|mắt|chi tiết thêm
const RAW = `cat|Mèo|tri|round|m|tri|n|whisk
fox|Cáo|tri|wide|w|dot|n|
wolf|Sói|tri|tall|m|dot|n|
dog|Chó|flop|round|m|dot|n|tongue
bear|Gấu|rnd|round|m|dot|n|
panda|Gấu trúc|rnd|round|m|dot|n|patch
polar|Gấu Bắc Cực|rnd|round|s|dot|n|
koala|Koala|big|round|s|dot|n|
rabbit|Thỏ|long|round|m|tri|n|cheek
mouse|Chuột|big|round|s|tri|n|whisk
hamster|Hamster|rnd|wide|s|tri|n|cheek
squirrel|Sóc|rnd|round|s|dot|n|tail,cheek
raccoon|Gấu mèo|rnd|round|s|dot|n|mask
redpanda|Gấu trúc đỏ|tri|wide|s|dot|n|stripe
tiger|Hổ|rnd|round|m|tri|n|stripe,whisk
lion|Sư tử|rnd|round|m|tri|n|mane
leopard|Báo|rnd|round|m|tri|n|spots
cow|Bò|flop|wide|w|pig|n|horn,spots
sheep|Cừu|flop|round|s|dot|n|wool
goat|Dê|flop|tall|s|dot|n|horn,beard
horse|Ngựa|tri|tall|w|pig|n|mohawk
unicorn|Kỳ lân|tri|tall|w|pig|n|uhorn,mohawk
zebra|Ngựa vằn|rnd|tall|w|pig|n|stripe,mohawk
donkey|Lừa|long|tall|w|pig|n|tuft
pig|Heo|tri|round|w|pig|n|cheek
boar|Lợn rừng|tri|wide|w|pig|n|tusk
elephant|Voi|big|wide|n|none|n|trunk
hippo|Hà mã|rnd|wide|w|pig|n|
rhino|Tê giác|rnd|wide|w|dot|n|rhorn
giraffe|Hươu cao cổ|rnd|tall|s|dot|n|knob,spots
deer|Hươu|tri|round|s|dot|n|antler,spots
camel|Lạc đà|rnd|tall|w|pig|n|tuft
llama|Llama|long|tall|s|dot|n|tuft
monkey|Khỉ|big|round|f|dot|n|
gorilla|Khỉ đột|rnd|wide|f|dot|n|tuft
sloth|Lười|rnd|round|f|dot|n|mask
kangaroo|Chuột túi|long|tall|m|tri|n|whisk
otter|Rái cá|rnd|wide|m|dot|n|whisk
beaver|Hải ly|rnd|round|m|dot|n|teeth
hedgehog|Nhím|rnd|round|f|dot|n|spikes
bat|Dơi|tri|round|s|dot|n|wing,fang
owl|Cú|tri|round|f|beak|big|
penguin|Cánh cụt|none|round|f|beak|n|cheek
chicken|Gà|none|round|n|beak|n|comb
duck|Vịt|none|round|n|bill|n|cheek
parrot|Vẹt|none|round|f|beak|n|crest
frog|Ếch|none|wide|n|smile|top|cheek
turtle|Rùa|none|round|s|smile|n|shell
dino|Khủng long|none|wide|w|pig|n|spikes
seal|Hải cẩu|none|round|s|dot|n|whisk`;

const animals = {};
for (const row of RAW.split("\n")) {
  const [k, name, e, h, m, no, ey, ex] = row.split("|");
  const xs = (ex || "").split(",").filter(Boolean).map((x) => {
    if (!X[x]) throw new Error(`Chi tiết không tồn tại: ${x} (${k})`);
    return X[x];
  });
  const layer = (l) => xs.filter((x) => x[0] === l).map((x) => x[1]).join("");
  for (const [part, table] of [[e, E], [h, H], [m, MU], [ey, EY], [no, NO]]) {
    if (!(part in table)) throw new Error(`Mảnh không tồn tại: ${part} (${k})`);
  }
  animals[k] = { name, svg: layer("B") + E[e] + H[h] + layer("M") + MU[m] + EY[ey] + NO[no] + layer("F") };
}

const palettes = [
  { fur: "#E8702A", belly: "#FFF1DE", accent: "#7A3511", bg: "#FFE4C4" },
  { fur: "#9AA3AE", belly: "#EEF1F4", accent: "#F2A6B5", bg: "#D6E8FA" },
  { fur: "#8B5E3C", belly: "#E8CDA8", accent: "#4A2E1A", bg: "#D9F0D0" },
  { fur: "#F4F1EC", belly: "#FFFFFF", accent: "#3A3A42", bg: "#F7D6E6" },
  { fur: "#7C6BD6", belly: "#E4DEFA", accent: "#F2C14E", bg: "#FFF3C7" },
  { fur: "#4FB6A3", belly: "#DDF5EF", accent: "#E35D6A", bg: "#FBE0DC" },
  { fur: "#F08CA8", belly: "#FFE6EE", accent: "#B03A66", bg: "#E6F4FF" },
  { fur: "#F2C14E", belly: "#FFF6D6", accent: "#B8741A", bg: "#DCEBFF" },
  { fur: "#6DAA4E", belly: "#E8F5DA", accent: "#2F5B2A", bg: "#FFEBD0" },
  { fur: "#3E4A63", belly: "#C9D2E6", accent: "#7DD3E8", bg: "#E9E4F5" },
];

const accessories = {
  none: { name: "Không", svg: "" },
  glasses: { name: "Kính", svg: '<circle cx="39" cy="52" r="8" fill="none" stroke="#222" stroke-width="2"/><circle cx="61" cy="52" r="8" fill="none" stroke="#222" stroke-width="2"/><path d="M47 52h6" stroke="#222" stroke-width="2"/>' },
  party: { name: "Mũ tiệc", svg: '<polygon points="38,30 62,30 50,2" fill="#E35D6A"/><circle cx="50" cy="3" r="3.5" fill="#F2C14E"/><path d="M42 22h16M45 14h10" stroke="#fff" stroke-width="2"/>' },
  crown: { name: "Vương miện", svg: '<polygon points="34,30 34,12 42,20 50,8 58,20 66,12 66,30" fill="#F2C14E" stroke="#C99A1E" stroke-width="1.5"/>' },
  bow: { name: "Nơ", svg: '<polygon points="50,88 36,80 36,96" fill="#E35D6A"/><polygon points="50,88 64,80 64,96" fill="#E35D6A"/><circle cx="50" cy="88" r="4" fill="#B93A47"/>' },
  phones: { name: "Tai nghe", svg: '<path d="M20 56q0-36 30-36t30 36" stroke="#333" stroke-width="4" fill="none"/><rect x="14" y="50" width="11" height="17" rx="4" fill="#E35D6A"/><rect x="75" y="50" width="11" height="17" rx="4" fill="#E35D6A"/>' },
};

const lib = {
  version: VERSION,
  // avatar_key = animal:palette:accessory (palette là chỉ số trong mảng palettes)
  keyFormat: "animal:palette:accessory",
  viewBox: "0 0 100 100",
  animals,
  palettes,
  accessories,
};

const json = JSON.stringify(lib, null, 1) + "\n";
const targets = [
  join(here, `avatar-lib.v${VERSION}.json`),
  join(root, "mobile", "assets", "avatar", `avatar-lib.v${VERSION}.json`),
];
for (const t of targets) {
  mkdirSync(dirname(t), { recursive: true });
  writeFileSync(t, json);
}
// Hàm vẽ chuẩn. Client (Dart) phải cho ra đúng chuỗi này; mẫu đối chiếu ghi vào test fixture.
export function render(l, key) {
  const [animal, palette, accessory] = key.split(":");
  const p = l.palettes[Number(palette)];
  const body = l.animals[animal].svg
    .replaceAll("{{fur}}", p.fur).replaceAll("{{belly}}", p.belly).replaceAll("{{accent}}", p.accent);
  return `<svg viewBox="${l.viewBox}" xmlns="http://www.w3.org/2000/svg"><rect width="100" height="100" fill="${p.bg}"/>${body}${l.accessories[accessory].svg}</svg>`;
}
const sampleKeys = ["fox:0:none", "cat:3:glasses", "elephant:9:phones", "lion:4:crown", "frog:5:bow", "seal:1:party"];
const samples = Object.fromEntries(sampleKeys.map((k) => [k, render(lib, k)]));
const samplesPath = join(root, "mobile", "test", "fixtures", `avatar-lib.v${VERSION}.samples.json`);
mkdirSync(dirname(samplesPath), { recursive: true });
writeFileSync(samplesPath, JSON.stringify(samples, null, 1) + "\n");

const total = Object.keys(animals).length * palettes.length * Object.keys(accessories).length;
console.log(`avatar-lib v${VERSION}: ${Object.keys(animals).length} con vật × ${palettes.length} màu × ${Object.keys(accessories).length} phụ kiện = ${total} avatar`);
