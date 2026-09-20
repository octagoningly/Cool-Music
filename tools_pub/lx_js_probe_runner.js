#!/usr/bin/env node

const crypto = require('crypto')
const fs = require('fs')
const vm = require('vm')

const TARGETS = [
  {
    sourceId: 'kw',
    songMid: '228908',
    name: '\u6674\u5929',
    artist: '\u5468\u6770\u4f26',
    durationSec: 269,
    albumName: '\u53f6\u60e0\u7f8e',
  },
  {
    sourceId: 'kg',
    songMid: '20505418',
    name: '\u6674\u5929',
    artist: '\u5468\u6770\u4f26',
    durationSec: 269,
    albumName: '\u53f6\u60e0\u7f8e',
    qualityHashes: {
      '128k': 'B3A52A7A958BF0AED0EBFBA2E9A818B7',
      '320k': '1B56126A8A03924F1DD066259C095CBC',
      flac: '0A69169202DE95AAF24A9944CCF0730D',
    },
  },
  {
    sourceId: 'tx',
    songMid: '0039MnYb0qxYhV',
    name: '\u6674\u5929',
    artist: '\u5468\u6770\u4f26',
    durationSec: 269,
    albumName: '\u53f6\u60e0\u7f8e',
  },
]

function parseArgs(argv) {
  const args = {}
  for (let i = 2; i < argv.length; i += 1) {
    const key = argv[i]
    const value = argv[i + 1]
    if (!key.startsWith('--')) continue
    args[key.slice(2)] = value
    i += 1
  }
  return args
}

function decodeMaybeUri(value) {
  try {
    return decodeURIComponent(value)
  } catch (_) {
    return value
  }
}

function aesEncrypt(dataB64, keyB64, ivB64, mode) {
  try {
    const data = Buffer.from(dataB64, 'base64')
    const key = Buffer.from(keyB64, 'base64')
    const iv = ivB64 ? Buffer.from(ivB64, 'base64').subarray(0, 16) : null
    const algorithm = mode && mode.includes('CBC') ? 'aes-128-cbc' : 'aes-128-ecb'
    const cipher = crypto.createCipheriv(algorithm, key, algorithm.includes('ecb') ? null : iv)
    if (algorithm.includes('ecb')) cipher.setAutoPadding(false)
    return Buffer.concat([cipher.update(data), cipher.final()]).toString('base64')
  } catch (_) {
    return ''
  }
}

function rsaEncrypt(dataB64, publicKey, padding) {
  try {
    const normalized = publicKey.includes('BEGIN PUBLIC KEY')
      ? publicKey
      : '-----BEGIN PUBLIC KEY-----\n' +
          publicKey.replace(/\s+/g, '').match(/.{1,64}/g).join('\n') +
          '\n-----END PUBLIC KEY-----'
    const encrypted = crypto.publicEncrypt(
      {
        key: normalized,
        padding: padding && padding.includes('OAEP')
          ? crypto.constants.RSA_PKCS1_OAEP_PADDING
          : crypto.constants.RSA_NO_PADDING,
      },
      Buffer.from(dataB64, 'base64')
    )
    return encrypted.toString('base64')
  } catch (_) {
    return ''
  }
}

function parseBody(text) {
  try {
    return JSON.parse(text)
  } catch (_) {
    return text
  }
}

function buildMusicInfo(hit, quality) {
  const interval = `${String(Math.floor(hit.durationSec / 60)).padStart(2, '0')}:${String(hit.durationSec % 60).padStart(2, '0')}`
  const musicInfo = {
    name: hit.name,
    singer: hit.artist,
    source: hit.sourceId,
    songmid: hit.songMid,
    interval,
    albumName: hit.albumName,
    img: '',
    albumId: '',
    types: [],
    _types: {},
    typeUrl: {},
  }
  if (hit.qualityHashes) {
    if (hit.qualityHashes[quality]) musicInfo.hash = hit.qualityHashes[quality]
    for (const [qualityKey, hash] of Object.entries(hit.qualityHashes)) {
      musicInfo.types.push({ type: qualityKey, hash })
      musicInfo._types[qualityKey] = { hash }
    }
  }
  return musicInfo
}

function selectQuality(qualities) {
  const allowed = qualities.size > 0 ? qualities : new Set(['128k', '320k', 'flac', 'flac24bit'])
  return ['320k', '128k', 'flac', 'flac24bit'].find((quality) => allowed.has(quality)) || '128k'
}

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function withTimeout(promise, ms) {
  return Promise.race([
    promise,
    wait(ms).then(() => null),
  ])
}

async function main() {
  const args = parseArgs(process.argv)
  const sourceFile = args['source-file']
  const preloadFile = args.preload
  if (!sourceFile || !preloadFile) {
    throw new Error('usage: lx_js_probe_runner.js --source-file <file> --preload <file>')
  }

  const script = fs.readFileSync(sourceFile, 'utf8')
  const preload = fs.readFileSync(preloadFile, 'utf8')
  const key = `probe_${Date.now()}`
  let supportedSources = new Set()
  let supportedQualities = new Set()
  let lastFailure = ''
  const pendingMusicUrl = new Map()
  const abortControllers = new Map()

  const context = {
    console: {
      log: () => {},
      info: () => {},
      warn: () => {},
      error: () => {},
    },
    setTimeout,
    clearTimeout,
    Uint8Array,
    ArrayBuffer,
    TextEncoder,
    TextDecoder,
  }
  context.globalThis = context

  function callJs(action, payload) {
    if (typeof context.__lx_native__ !== 'function') return
    context.__lx_native__(key, action, payload == null ? null : JSON.stringify(payload))
  }

  async function handleHttpRequest(data) {
    const requestKey = data.requestKey
    const url = data.url
    const options = data.options || {}
    if (!requestKey || !url) return
    const controller = new AbortController()
    abortControllers.set(requestKey, controller)
    const timeout = setTimeout(() => controller.abort(), Math.min(Number(options.timeout || 13000), 60000))
    try {
      const method = String(options.method || 'get').toUpperCase()
      const headers = Object.assign(
        {
          Accept: 'application/json',
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36',
        },
        options.headers || {}
      )
      const request = { method, headers, signal: controller.signal }
      if (method === 'POST') {
        if (options.form && typeof options.form === 'object') {
          request.body = new URLSearchParams(options.form).toString()
          request.headers['Content-Type'] = 'application/x-www-form-urlencoded'
        } else if (options.body != null) {
          request.body = typeof options.body === 'string' ? options.body : JSON.stringify(options.body)
          request.headers['Content-Type'] = request.headers['Content-Type'] || 'application/json; charset=utf-8'
        } else {
          request.body = ''
        }
      }
      const response = await fetch(url, request)
      const bodyText = await response.text()
      const headersObject = {}
      response.headers.forEach((value, name) => {
        headersObject[name] = headersObject[name] ? headersObject[name] + value : value
      })
      if (!response.ok) lastFailure = `HTTP ${response.status}`
      callJs('response', {
        requestKey,
        error: null,
        response: {
          statusCode: response.status,
          statusMessage: response.statusText,
          headers: headersObject,
          body: parseBody(bodyText),
        },
      })
    } catch (error) {
      lastFailure = error && error.message ? error.message : 'request failed'
      callJs('response', {
        requestKey,
        error: lastFailure,
        response: null,
      })
    } finally {
      clearTimeout(timeout)
      abortControllers.delete(requestKey)
    }
  }

  context.__lx_native_call__ = (callKey, action, data) => {
    if (callKey !== key) return null
    if (action === 'init') {
      const payload = JSON.parse(data || '{}')
      const sources = payload.info && payload.info.sources ? payload.info.sources : {}
      supportedSources = new Set(Object.keys(sources))
      supportedQualities = new Set()
      for (const source of Object.values(sources)) {
        for (const quality of source.qualitys || []) supportedQualities.add(quality)
      }
    } else if (action === 'request') {
      handleHttpRequest(JSON.parse(data || '{}'))
    } else if (action === 'cancelRequest') {
      const controller = abortControllers.get(data)
      if (controller) controller.abort()
    } else if (action === 'response') {
      const payload = JSON.parse(data || '{}')
      const requestKey = payload.requestKey
      const resolve = pendingMusicUrl.get(requestKey)
      if (!resolve) return null
      pendingMusicUrl.delete(requestKey)
      const url = payload.status && payload.result && payload.result.data
        ? payload.result.data.url
        : ''
      if (!url && payload.errorMessage) lastFailure = String(payload.errorMessage).slice(0, 200)
      resolve(url && /^https?:/.test(url) ? url : null)
    }
    return null
  }
  context.__lx_native_call__set_timeout = (id, delay) => {
    setTimeout(() => callJs('__set_timeout__', id), Number(delay || 0))
    return null
  }
  context.__lx_native_call__utils_str2b64 = (value) => Buffer.from(String(value || ''), 'utf8').toString('base64')
  context.__lx_native_call__utils_b642buf = (value) => JSON.stringify(Array.from(Buffer.from(String(value || ''), 'base64')))
  context.__lx_native_call__utils_str2md5 = (value) => crypto.createHash('md5').update(decodeMaybeUri(String(value || ''))).digest('hex')
  context.__lx_native_call__utils_aes_encrypt = aesEncrypt
  context.__lx_native_call__utils_rsa_encrypt = rsaEncrypt

  vm.createContext(context, { codeGeneration: { strings: true, wasm: false } })
  vm.runInContext(preload, context, { timeout: 2000, filename: 'user-api-preload.js' })
  context.lx_setup(key, 'probe', 'Probe Source', '', '', '', '', script)
  vm.runInContext(script, context, { timeout: 3000, filename: 'lx-source.js' })

  const initStart = Date.now()
  while (supportedSources.size === 0 && Date.now() - initStart < 2500) {
    await wait(50)
  }

  const quality = selectQuality(supportedQualities)
  const healthyChannels = []
  const details = {}
  for (const hit of TARGETS) {
    if (supportedSources.size > 0 && !supportedSources.has(hit.sourceId)) continue
    lastFailure = ''
    const requestKey = `music_${Date.now()}_${Math.random().toString(16).slice(2)}`
    const promise = new Promise((resolve) => pendingMusicUrl.set(requestKey, resolve))
    callJs('request', {
      requestKey,
      data: {
        source: hit.sourceId,
        action: 'musicUrl',
        info: {
          type: quality,
          musicInfo: buildMusicInfo(hit, quality),
        },
      },
    })
    const url = await withTimeout(promise, 9000)
    if (url) healthyChannels.push(hit.sourceId)
    details[hit.sourceId] = url || lastFailure || 'failed'
  }

  console.log(JSON.stringify({
    supportedSources: Array.from(supportedSources),
    supportedQualities: Array.from(supportedQualities),
    healthyChannels,
    details,
  }))
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : String(error))
  process.exit(1)
})
