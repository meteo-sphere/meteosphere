#version 300 es
// The stars, rain and snow over a still picture of the sky, drawn by SkyRenderer at the picture's full size over its
// averaged clouds, on any Android: sky_stars.agsl and sky_drops.agsl as they are, which draw them over the live sky
// from Android 13, but for the live drops' fading out down a view: on a still they fall all the way down; and its rain
// sized by a screen's height, rainHeight, not the picture's. The stars are added over the sky as much as the clouds and
// the mist let them through; the drops are laid over it all.

precision highp float;

// The composed picture, twice as high: the sky on top, how far it can be seen through the clouds and the mist below
uniform sampler2D picture;
// 256 x 256 value noise, repeating: two random numbers per texel, red and green
uniform sampler2D noise;
// The moon's nearside, NASA's map, as sky_moon.agsl's: longitude -90 to 90 degrees across, latitude 90 to -90 down
// (texture rows from the top); its phase angle, and where its bright limb and its north point
uniform sampler2D moonMap;
uniform vec3 disc;
// The picture in pixels, and the part of the 2:3 sky it shows: left, top, width, height, 0 to 1
uniform vec2 size;
uniform vec4 view;
// The stars' twinkle and the drops' fall, in seconds
uniform float starsTime;
uniform float dropsTime;
uniform float coverage;
// Where the sun or the moon stands on the sky, 0 to 1, and which: 0 none, 1 the sun, 2 the moon
uniform vec3 body;
uniform float stars;
// Where the clouds thin out down the picture, 0 its top to 1 its bottom; from below 0, nowhere
uniform vec2 clear;
// How hard it rains and snows, 0 to 1, and how bright the drops are: dimmer at night
uniform float rain;
uniform float snow;
// The height the rain's drops are sized by, in the picture's pixels: as tall as the screen would be at the picture's
// scale, so that a card's small picture has them as long and as thin as the city's screen, not squeezed into its
// few rows as the snow is
uniform float rainHeight;
uniform float brightness;

out vec4 outColor;

const float MOON_RADIUS = 0.031;
const float HORIZON = 0.45;
const float SPAN = 1.0;

vec3 ray(vec2 f) {
    float e = (HORIZON - f.y) * SPAN;
    float u = (f.x - 0.5) * 0.6667 * SPAN;
    return normalize(vec3(u, sin(e), cos(e)));
}

// As the shaders' BitmapShader of the noise reads it, linearly filtered and repeating, at a point of its texels
vec4 noiseAt(vec2 p) {
    return texture(noise, (fract(p / 256.0) * 256.0 + 0.5) / 256.0);
}

float hash(vec2 p) {
    return noiseAt(floor(p)).r;
}

float veil(vec2 xy) {
    return clear.x < 0.0 ? 0.0 : smoothstep(clear.x, clear.y, xy.y / size.y);
}

// --- sky_stars.agsl ---

vec4 star(vec2 px, float cell, float share, float power, float salt) {
    vec2 c = floor(px / cell);
    float r = hash(c + vec2(salt, 13.0));
    if (r > share) {
        return vec4(0.0);
    }
    float g = hash(c + vec2(91.0 + salt, 37.0));
    float k = r / share;
    vec2 at = (c + vec2(0.15 + 0.7 * g, 0.15 + 0.7 * fract(k * 7.31 + g * 3.7))) * cell;
    float bright = pow(fract(k * 13.17 + g * 5.3), power);
    float sigma2 = 0.28 + 0.5 * bright;
    float d2 = dot(px - at, px - at);
    float light = exp(-d2 / sigma2) + 0.06 * bright * exp(-sqrt(d2) / 2.2);
    float twinkle = 0.8 + 0.2 * sin(starsTime * (1.3 + 3.0 * g) + 6.28 * k);
    vec3 tint = mix(vec3(0.78, 0.86, 1.0), vec3(1.0, 0.88, 0.74), smoothstep(0.55, 1.0, g));
    return vec4(tint, 1.0) * (light * bright * mix(1.0, twinkle, bright));
}

vec3 starsAt(vec2 xy, float through) {
    vec2 px = xy + view.xy * size / view.zw;
    float scale = clamp(size.x / 1080.0, 0.8, 2.0);
    vec4 s = star(px / scale, 15.0, 0.42, 2.5, 0.0) * 1.1 + star(px / scale, 46.0, 0.6, 1.4, 57.0) * 1.8;
    if (s.w < 0.004) {
        return vec3(0.0);
    }
    vec2 f = view.xy + xy / size * view.zw;
    vec3 rd = ray(f);
    float thin = veil(xy);
    float seen = stars * smoothstep(0.02, 0.2, rd.y) * (1.0 - 0.5 * thin)
            * (1.0 - thin * smoothstep(0.35, 0.95, coverage));
    if (body.z > 1.5) {
        vec3 bd = ray(body.xy);
        seen *= smoothstep(MOON_RADIUS * 1.1, MOON_RADIUS * 1.4, length(rd - bd));
    }
    if (seen <= 0.0) {
        return vec3(0.0);
    }
    return clamp(s.rgb * seen * through, 0.0, 1.0);
}

// --- sky_moon.agsl ---

vec3 graded(vec3 col) {
    float lum = dot(col, vec3(0.2126, 0.7152, 0.0722));
    col = mix(vec3(lum), col, 0.85);
    return mix(col, (col - 0.5) * 1.12 + vec3(0.5, 0.5, 0.48), smoothstep(0.08, 0.35, lum));
}

vec3 shoulder(vec3 col) {
    return mix(col, 0.72 + 0.16 * (1.0 - exp(-(col - 0.72) * 5.0)), step(0.72, col));
}

vec3 unshoulder(vec3 col) {
    return mix(col, 0.72 - log(max(1.0 - (col - 0.72) / 0.16, 0.0001)) / 5.0, step(0.72, col));
}

// The moon's light at a pixel, graded as the frame, and how much of it shows through the clouds and the mist in a:
// added to what is there, the sum's highlights soft as the frame's
vec4 moonAt(vec2 xy, float through) {
    vec2 f = view.xy + xy / size * view.zw;
    vec3 rd = ray(f);
    vec3 bd = ray(body.xy);
    vec3 right = normalize(cross(vec3(0.0, 1.0, 0.0), bd));
    vec3 up = cross(bd, right);
    vec2 local = vec2(dot(rd - bd, right), dot(rd - bd, up)) / MOON_RADIUS;
    float r2 = dot(local, local);
    if (r2 >= 1.0) {
        return vec4(0.0);
    }
    vec3 n = vec3(local, sqrt(1.0 - r2));
    vec2 limb = vec2(-sin(disc.y), cos(disc.y));
    vec3 sun = vec3(limb * sin(disc.x), cos(disc.x));
    float lit = smoothstep(-0.04, 0.04, dot(n, sun));
    vec2 north = vec2(-sin(disc.z), cos(disc.z));
    vec2 east = vec2(cos(disc.z), sin(disc.z));
    float lat = asin(clamp(dot(local, north), -1.0, 1.0));
    float lon = atan(dot(local, east), n.z);
    vec3 albedo = texture(moonMap, vec2(0.5 + lon / 3.14159265, 0.5 - lat / 3.14159265)).rgb;
    vec3 moon = vec3(0.96, 0.95, 0.9) * albedo * (0.06 + 0.94 * lit);
    return vec4(graded(moon) * through, smoothstep(1.0, 0.9, r2));
}

// --- sky_drops.agsl ---

vec2 random2(vec2 cell) {
    return noiseAt(cell).rg;
}

float rainDepth(vec2 p, float depth) {
    float cellW = mix(10.0, 40.0, depth);
    float cellH = mix(70.0, 330.0, depth);
    float len = mix(16.0, 150.0, depth);
    float speed = mix(650.0, 1500.0, depth);
    float width = mix(0.7, 2.4, depth);
    vec2 q = vec2(p.x + p.y * 0.12, p.y - dropsTime * speed);
    vec2 cell = floor(q / vec2(cellW, cellH));
    vec2 r = random2(cell + vec2(depth * 61.0, 17.0));
    if (r.x > mix(0.25, 0.95, rain)) {
        return 0.0;
    }
    vec2 local = q - cell * vec2(cellW, cellH);
    float x0 = cellW * (0.2 + 0.6 * r.y);
    float y0 = (cellH - len) * fract(r.x * 7.31 + r.y * 3.17);
    float along = (local.y - y0) / len;
    if (along < 0.0 || along > 1.0) {
        return 0.0;
    }
    float across = smoothstep(width, 0.0, abs(local.x - x0));
    return across * along * mix(0.3, 0.13, depth);
}

float snowDepth(vec2 p, float depth, float wind) {
    float cell = mix(16.0, 120.0, depth);
    float speed = mix(28.0, 150.0, depth);
    float radius = 0.6 + 9.0 * pow(depth, 2.2);
    float defocus = max(depth - 0.35, 0.0);
    float blur = mix(0.5, 1.0, depth) + 26.0 * defocus * defocus;
    float alpha = mix(0.45, 0.85, smoothstep(0.0, 0.35, depth)) * mix(1.0, 0.32, smoothstep(0.35, 1.0, depth));
    vec2 q = vec2(p.x + speed * wind, p.y - dropsTime * speed);
    vec2 c = floor(q / cell);
    vec2 r = random2(c + vec2(depth * 47.0, 91.0));
    if (r.x > mix(0.35, 0.95, snow)) {
        return 0.0;
    }
    float r3 = fract(r.x * 13.7 + r.y * 7.1);
    vec2 center = (c + vec2(0.15 + 0.7 * r.y, 0.15 + 0.7 * r3)) * cell;
    vec2 d = q - center;
    d.y /= 1.0 + 0.35 * depth;
    float flake = radius * (0.6 + 0.8 * r3);
    if (abs(d.y) > flake + blur || abs(d.x) > flake + blur + cell * 0.17) {
        return 0.0;
    }
    d.x -= (sin(dropsTime * (0.7 + r.y) + r.x * 6.28) + 0.4 * sin(dropsTime * (1.9 + r3) + r.y * 6.28)) * cell * 0.12;
    float dist = length(d);
    if (dist > flake + blur) {
        return 0.0;
    }
    return smoothstep(flake + blur, max(flake - blur * 0.5, 0.0), dist) * alpha * (0.7 + 0.3 * r.y);
}

float dropsAt(vec2 xy) {
    vec2 p = xy / size.y * 800.0;
    float a = 0.0;
    if (rain > 0.0) {
        vec2 q = xy / rainHeight * 800.0;
        a += rainDepth(q, 0.0) + rainDepth(q, 0.5) + rainDepth(q, 1.0);
        a += 0.12 * rain * smoothstep(0.35, 1.0, xy.y / size.y);
    }
    if (snow > 0.0) {
        float wind = 0.35 * dropsTime - 1.087 * cos(dropsTime * 0.23) - 0.164 * cos(dropsTime * 0.61 + 1.3);
        for (int i = 0; i < 5; i++) {
            a += snowDepth(p, float(i) * 0.25, wind);
        }
    }
    return clamp(a * brightness, 0.0, 1.0);
}

void main() {
    vec2 whole = vec2(size.x, size.y * 2.0);
    if (gl_FragCoord.y < size.y) {
        // The bottom half, how far one sees through, as it is
        outColor = texture(picture, gl_FragCoord.xy / whole);
        return;
    }
    // The picture's pixel from its top, as the views and the AGSL shaders count; GL counts rows from the bottom, the
    // sky's rows the upper half, the see-through's the lower, the same way up
    vec2 xy = vec2(gl_FragCoord.x, size.y - (gl_FragCoord.y - size.y));
    vec3 c = texture(picture, gl_FragCoord.xy / whole).rgb;
    float through = texture(picture, vec2(gl_FragCoord.x, gl_FragCoord.y - size.y) / whole).r;
    if (body.z > 1.5) {
        vec4 moon = moonAt(xy, through);
        c = mix(c, shoulder(unshoulder(c) + moon.rgb), moon.a);
    }
    if (stars > 0.0) {
        c = min(c + starsAt(xy, through), vec3(1.0));
    }
    if (rain > 0.0 || snow > 0.0) {
        float a = dropsAt(xy);
        c = c * (1.0 - a) + a;
    }
    outColor = vec4(c, 1.0);
}
