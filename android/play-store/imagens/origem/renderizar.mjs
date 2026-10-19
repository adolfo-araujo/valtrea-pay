// Converte os SVG da loja em PNG e recorta as capturas de tela para a proporção aceita pela Play Store.
// Uso: node renderizar.mjs <pasta-entrada> <pasta-saida>   (precisa de @resvg/resvg-js e sharp instalados)
import { readFile, readdir, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { createRequire } from 'node:module'

const require = createRequire(process.env.MODULOS + '/')
const { Resvg } = require('@resvg/resvg-js')
const sharp = require('sharp')

const [entrada, saida] = process.argv.slice(2)

for (const nome of await readdir(entrada)) {
  if (nome.endsWith('.svg')) {
    const svg = await readFile(join(entrada, nome))
    const png = new Resvg(svg, { font: { loadSystemFonts: true, defaultFontFamily: 'Inter' } }).render().asPng()
    await writeFile(join(saida, nome.replace('.svg', '.png')), png)
    console.log('gerado', nome.replace('.svg', '.png'))
  } else if (nome.startsWith('tela-') && nome.endsWith('.png')) {
    // 1080x2400 tem proporção maior que 2:1, que a Play Store recusa. Tira a barra de status (120 px) e alarga
    // 30 px de cada lado repetindo a cor da borda: 1140x2280, exatamente 2:1, sem cortar o rodapé.
    await sharp(join(entrada, nome))
      .extract({ left: 0, top: 120, width: 1080, height: 2280 })
      .extend({ left: 30, right: 30, extendWith: 'copy' })
      .toFile(join(saida, nome))
    console.log('recortado', nome)
  }
}
