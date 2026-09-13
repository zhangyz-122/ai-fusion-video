import fs from 'node:fs';
import path from 'node:path';

const input = process.argv[2];
const outputDir = process.argv[3];
if (!input || !outputDir) throw new Error('usage: node extract_universal_director.mjs input.json output-dir');

const wf = JSON.parse(fs.readFileSync(input, 'utf8'));
const nodes = new Map((wf.nodes || []).map((node) => [String(node.id), node]));
const links = new Map((wf.links || []).map((link) => [String(link[0]), { origin: String(link[1]), slot: Number(link[2]) }]));

function inputDefaults(node) {
  if (node.widgets_values_named && typeof node.widgets_values_named === 'object') {
    return { ...node.widgets_values_named };
  }
  const result = {};
  let widgetIndex = 0;
  for (const input of node.inputs || []) {
    if (input.widget && input.name && node.widgets_values?.[widgetIndex] !== undefined) {
      result[input.name] = node.widgets_values[widgetIndex++];
    }
  }
  return result;
}

function branch(name, ids, overrides) {
  const selected = new Set(ids.map(String));
  const api = {};
  for (const id of selected) {
    const node = nodes.get(id);
    if (!node || ['SetNode', 'GetNode', 'Reroute'].includes(node.type)) continue;
    const inputs = inputDefaults(node);
    for (const input of node.inputs || []) {
      if (input.link == null) continue;
      const ref = links.get(String(input.link));
      if (ref && selected.has(ref.origin)) inputs[input.name] = [ref.origin, ref.slot];
    }
    api[id] = {
      _meta: { title: node.title || node.properties?.['Node name for S&R'] || node.type },
      class_type: node.type,
      inputs,
    };
  }
  for (const [id, values] of Object.entries(overrides)) {
    if (!api[id]) continue;
    Object.assign(api[id].inputs, values);
  }
  return api;
}

const commonOutput = (prefix) => ({
  frame_rate: 16,
  loop_count: 0,
  filename_prefix: prefix,
  format: 'video/h264-mp4',
  pingpong: false,
  save_output: true,
});

const i2v = branch('WAN_I2V_STANDARD', [201, 202, 203, 204, 205, 206, 207, 209, 211, 212, 213, 214, 215], {
  '207': { image: 'ep001_sh01_start.png' },
  '203': { vace_blocks_to_swap: 0 },
  // The locally available Wan I2V checkpoint is fp8_e4m3fn (not scaled).
  // Keep the quantization binding truthful so ComfyUI admits the workflow.
  '204': { quantization: 'fp8_e4m3fn' },
  '209': { keep_proportion: 'stretch', crop_position: 'center', device: 'cpu' },
  '205': { positive_prompt: 'pure 2D Chinese donghua, single character seated at a desk, hears a sound outside and gently raises her eyes, stable identity, subtle natural motion, coherent composition, no text, no subtitles, no watermark' },
  '212': { width: 416, height: 240, num_frames: 17, noise_aug_strength: 0.0, start_latent_strength: 1.0, end_latent_strength: 1.0, force_offload: true, fun_or_fl2v_model: false, tiled_vae: false },
  '213': { steps: 4, cfg: 1.0, shift: 5.0, seed: 314159265, force_offload: true, scheduler: 'dpm++_sde', riflex_freq_index: 0, rope_function: 'comfy' },
  '214': { enable_vae_tiling: true, tile_x: 272, tile_y: 272, tile_stride_x: 144, tile_stride_y: 128, normalization: 'default' },
  '215': commonOutput('Codex/WAN_I2V_STANDARD'),
});

const flf = branch('WAN_FLF_STANDARD', [201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215], {
  '207': { image: 'ep001_sh01_start.png' },
  '208': { image: 'ep001_SH02_SYSTEM_in.png' },
  '203': { vace_blocks_to_swap: 0 },
  '204': { quantization: 'fp8_e4m3fn' },
  '209': { keep_proportion: 'stretch', crop_position: 'center', device: 'cpu' },
  '210': { keep_proportion: 'stretch', crop_position: 'center', device: 'cpu' },
  '205': { positive_prompt: 'pure 2D Chinese donghua, transition from the first keyframe to the last keyframe, preserve character identity and scene geometry, coherent start-to-end motion, no text, no subtitles, no watermark' },
  '212': { width: 416, height: 240, num_frames: 17, noise_aug_strength: 0.0, start_latent_strength: 1.0, end_latent_strength: 1.0, force_offload: true, fun_or_fl2v_model: true, tiled_vae: false },
  '213': { steps: 4, cfg: 1.0, shift: 5.0, seed: 271828182, force_offload: true, scheduler: 'dpm++_sde', riflex_freq_index: 0, rope_function: 'comfy' },
  '214': { enable_vae_tiling: true, tile_x: 272, tile_y: 272, tile_stride_x: 144, tile_stride_y: 128, normalization: 'default' },
  '215': commonOutput('Codex/WAN_FLF_STANDARD'),
});

const talk = branch('WAN_INFINITETALK', [301, 302, 304, 305, 306, 307, 308, 309, 310, 311, 312, 313, 314, 315, 316], {
  '301': { audio: 'h3_voice_ref_wangtianwei.wav' },
  '302': { model: 'wav2vec2-chinese-base_fp16.safetensors', base_precision: 'fp16', load_device: 'main_device' },
  '304': { num_frames: 17 },
  '307': { quantization: 'fp8_e4m3fn' },
  '308': { positive_prompt: 'close-up cinematic portrait, natural speaking motion, stable identity, subtle expression and mouth movement, pure 2D Chinese donghua, no text, no subtitles, no watermark' },
  '310': { image: 'character_1.png' },
  '311': { width: 416, height: 240, keep_proportion: 'stretch', crop_position: 'center', device: 'cpu' },
  '313': { width: 416, height: 240, num_frames: 17 },
  '314': { steps: 6, cfg: 1.0, shift: 11.0, seed: 161803398, force_offload: true, scheduler: 'dpm++_sde', riflex_freq_index: 0, rope_function: 'comfy' },
  '316': { ...commonOutput('Codex/WAN_INFINITETALK') },
});

// The promoted runtime has the local wav2vec2 safetensors loader, while the
// historical Director graph used an online repository loader. Keep the graph
// shape and downstream links unchanged, but bind this branch to the local file.
talk['302'].class_type = 'Wav2VecModelLoader';
talk['302']._meta.title = 'Wav2VecModelLoader';

fs.mkdirSync(outputDir, { recursive: true });
for (const [name, api] of Object.entries({ WAN_I2V_STANDARD: i2v, WAN_FLF_STANDARD: flf, WAN_INFINITETALK: talk })) {
  fs.writeFileSync(path.join(outputDir, `${name}.json`), JSON.stringify(api, null, 2) + '\n');
}
console.log(JSON.stringify({ input, outputDir, workflows: Object.fromEntries(Object.entries({ WAN_I2V_STANDARD: i2v, WAN_FLF_STANDARD: flf, WAN_INFINITETALK: talk }).map(([name, api]) => [name, Object.keys(api).length])) }));
