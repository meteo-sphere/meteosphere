#version 300 es
// The weather's sky, seen from the ground, drawn by SkyRenderer with OpenGL ES 3: its colours from the horizon up,
// the sun or the moon, the milky way, the clouds as a lit volume above, mist and lightning. The stars and the drops
// are drawn over it at full size by sky_stars.agsl and sky_drops.agsl.
//
// The clouds follow Guerrilla's: their density as Schneider gave it in "The Real-time Volumetric Cloudscapes of
// Horizon: Zero Dawn" (SIGGRAPH 2015), its sampler in GPU Pro 7 ("Real-Time Volumetric Cloudscapes") and
// "Nubis: Authoring Real-Time Volumetric Cloudscapes with the Decima Engine" (SIGGRAPH 2017), and their light as
// Nubis 2017 gave it. The noise is Hillaire's TileableVolumeNoise (MIT), made as tools/sky_noise_atlas.py says.
// Where the sources leave a value open, the value chosen here says so.
//
// It is drawn in two passes, the file built twice by SkyRenderer. With CLOUDS_PASS, the clouds: as Horizon's
// ("The Real-time Volumetric Cloudscapes of Horizon: Zero Dawn": "every frame we use a quarter res buffer to update 1
// out of 16 pixels for each 4x4 pixel block ... we reproject the previous frame"), each frame marches the rays of one
// pixel in sixteen, by turns, and carries the rest over from the frame before, moved on as the wind moved the clouds;
// marching every pixel each frame cost them 20 ms on a console, and as much on a phone held up its screen. Each
// marched pixel starts its steps a little further on than the last time and is averaged with what it had, so its
// steps blend into a smooth picture. Without it, the compose pass: every frame the sky, the sun or the moon, the
// clouds as the first pass left them, the mist, the lightning, put together for the output.

precision highp float;
precision highp sampler3D;
precision highp sampler2D;

// The clouds' base shape, 128^3: Perlin-Worley remapped by a low-frequency Worley fBm, packed as GPU Pro 7 does it
uniform sampler3D shapeTex;
// The detail eroding it, 32^3: a Worley fBm
uniform sampler3D detailTex;
// The clouds as the clouds pass leaves them: their light from the sun or the moon in rgb and how much of what lies
// behind them they hide in a; the sky's light on them in rgb and how far away they are in a, over DEPTH_RANGE, so that
// it fits the 0 to 1 of a byte where half floats can't be drawn into
uniform sampler2D cloudsDirect;
uniform sampler2D cloudsAmbient;
// The clouds' frames reach this many pixels past the output's edges, so that what the wind brings into the view is
// already there to carry over
uniform float margin;
// How many times a pixel has been marched, for its steps' offsets
uniform float frame;
// The output in pixels, and the part of the 2:3 sky it shows: left, top, width, height, 0 to 1
uniform vec2 size;
uniform vec4 view;
uniform vec3 zenith;
uniform vec3 horizon;
// The light on the clouds from the sun or the moon, and from the sky around: the sky's on their tops; their bottoms
// get as much of the bottom colour as the sky around them is open, as the light under the clouds is shut out
uniform vec3 light;
uniform vec3 ambient;
uniform vec3 ambientTop;
// The weather's cloud coverage, 0 to 1, as the weather map's red channel; the cloud type, 0 stratus, 0.5
// stratocumulus, 1 cumulus, as its blue; precipitation, 0 to 1, as its green
uniform float coverage;
uniform float cloudType;
uniform float precipitation;
// How dense the clouds are: their extinction per unit of the sky for a density of 1
uniform float sigma;
// Where the sun or the moon stands on the sky, 0 to 1, and which: 0 none, 1 the sun, 2 the moon
uniform vec3 body;
uniform float glow;
uniform float stars;
// Mist or fog lying on the ground, 0 to 1: its extinction there is MIST_EXTINCTION times it
uniform float mist;
// A bolt of lightning: x its shape's seed, y how bright, 0 none; z where across the sky it strikes
uniform vec3 bolt;
// How far the wind has carried the clouds, in the sky's units, and how far they have changed shape
uniform float drift;
uniform float evolve;
uniform float seed;
// Where the clouds thin out down the output, 0 its top to 1 its bottom, below the texts over the sky; from below 0,
// nowhere
uniform vec2 clear;
// Lightning lighting the clouds, 0 to 1
uniform float flash;
// Steps through the clouds' layer
uniform float steps;

#ifdef CLOUDS_PASS
// What the pass does: 0 marches every pixel and averages it in, as a still does or a fresh start; 1 marches, into a
// quarter of the size, the one pixel of each 4x4 block the turn says, a texel for each block, so that the GPU's groups
// of neighbouring pixels all march or none does; 2 puts those marches in their places at full size and carries the
// rest over from the frame before; 3 fills every pixel from those marches, read smoothly between them, for a fresh
// start's first picture; 4 draws the weather map
uniform float mode;
// The pixel of each 4x4 block whose turn it is
uniform vec2 turnCell;
// The quarter-size marches, for mode 2
uniform sampler2D marchedDirect;
uniform sampler2D marchedAmbient;
// How much a marched pixel's new value counts against the one it had: 1 to forget it; 0, for mode 2, every pixel
// carried over, none marched
uniform float weight;
// How far the wind has carried the clouds since the frame before, in the sky's units
uniform float shift;
layout(location = 0) out vec4 outDirect;
layout(location = 1) out vec4 outAmbient;
#else
// How settled the clouds are, 0 just started to 1: while a pixel has few marches averaged, its steps' noise shows, and
// the clouds are read smoothed over their neighbours instead, as much as they are not settled
uniform float settled;
// The bolt drawn once a bolt into a picture of the output's top half, core and glow, by this program with boltPass 1;
// read from it with boltMapped 1, rather than walking its 40 steps at every pixel on each frame of the flash
uniform sampler2D boltMap;
uniform float boltMapped;
uniform float boltPass;
// Under the texts, the clouds' own even colour, and in the bottom half of the output how far the night can be seen
out vec4 outColor;
#endif

// The clouds' layer: Horizon's low clouds lie between 1500 and 4000 metres; a unit of the sky is 1500 m
const float CLOUD_BOTTOM = 1.0;
const float CLOUD_TOP = 2.667;
// Where the horizon lies down the 2:3 sky, and the angle a unit of its height spans: as one looks a little up
const float HORIZON = 0.45;
const float SPAN = 1.0;
// Chosen, the sources giving no scale: the base shape repeats every 4 units (6 km), the detail every 0.25 (375 m): finer, its 32 texels drew streaks down an overcast
const float SHAPE_SCALE = 1.0 / 4.0;
const float DETAIL_SCALE = 1.0 / 0.25;
// The detail is read turned against the world's axes: looking along one of them, a ray through a cloud's base crosses
// its repeats over and over, all the same column of it, and its average left lines running into the distance.
// Chosen: turned 35 degrees about the vertical and tilted 10, as the rays across the view averaged it most evenly
const mat3 DETAIL_AXES = mat3(0.8192, 0.0996, -0.5649, 0.0, 0.9848, 0.1736, 0.5736, -0.1422, 0.8067);
// Chosen: how far the wind skews a cloud's top ahead of its bottom (Nubis: height_fraction * wind * 500, at its own
// scale): 150 m, as more leaned the domes into sails
const float SKEW = 0.1;
// Nubis 2017's directional scattering: eccentricity 0.6, and a silver lining round the light; its intensity and
// spread chosen
const float ECCENTRICITY = 0.6;
const float SILVER_INTENSITY = 0.6;
const float SILVER_SPREAD = 0.1;
const vec3 WIND = vec3(1.0, 0.0, 0.35);
// Mist as a layer of fog on the ground, its density falling off with height (an exponential height fog), seen through
// as far as its optical depth along the ray lets: the view through it fades into its own light with the distance, as
// Horizon fades the clouds into the atmosphere over their depth. Chosen: for a mist of 0.65, the ground's visibility
// (3.912 / extinction, Koschmieder) about 1.5 units (2.3 km), and the fog thinning by e every 0.25 units (375 m) up
const float MIST_EXTINCTION = 4.0;
const float MIST_HEIGHT = 0.25;
// The clouds' depths kept over this, past the farthest a ray goes through the layer (CLOUD_TOP / 0.02); 1 for none
const float DEPTH_RANGE = 256.0;

// The ray through a point of the sky
vec3 ray(vec2 f) {
    float e = (HORIZON - f.y) * SPAN;
    float u = (f.x - 0.5) * 0.6667 * SPAN;
    return normalize(vec3(u, sin(e), cos(e)));
}

// Smooth noise for the milky way and the mist: the clouds' detail noise
float fbm(vec3 p) {
    return texture(detailTex, p * 0.25).r;
}

// The remap of the sources
float remap(float v, float lo, float hi, float toLo, float toHi) {
    return toLo + (v - lo) / (hi - lo) * (toHi - toLo);
}

// How much the clouds thin out at a point of the output, 0 not at all, 1 gone
float veil(vec2 xy) {
    return clear.x < 0.0 ? 0.0 : smoothstep(clear.x, clear.y, xy.y / size.y);
}

// Nubis 2017: the probability of cloud over the layer's height for a type of cloud, made of two remaps. The stratus
// one is the slides'; the stratocumulus and cumulus ones measured off the slides' pictures of them (30, 31), which
// give them only as pictures: the cumulus fading out from 0.64 to 0.8 of the layer, not near its top.
float gradient(float h, vec4 g) {
    return clamp(remap(h, g.x, g.y, 0.0, 1.0), 0.0, 1.0) * clamp(remap(h, g.z, g.w, 1.0, 0.0), 0.0, 1.0);
}

vec4 typeGradientOf(float type) {
    const vec4 STRATUS = vec4(0.0, 0.1, 0.2, 0.3);
    const vec4 STRATOCUMULUS = vec4(0.03, 0.15, 0.32, 0.47);
    const vec4 CUMULUS = vec4(0.0, 0.085, 0.64, 0.8);
    return type < 0.5 ? mix(STRATUS, STRATOCUMULUS, type * 2.0) : mix(STRATOCUMULUS, CUMULUS, type * 2.0 - 1.0);
}

// The weather map's coverage over a point of the ground. In Horizon the weather map places the clouds, patches of
// coverage the 3D noise then carves; the weather's coverage is the share of the ground under cloud, as seen from
// above, not of the volume, which seen this low would close the whole sky. Its patches here are the base shape's
// noise across a slice at a large scale, the share over (1 - coverage) holding cloud, thickest in their middles.
// Chosen: an overcast's patches about 2 units (3 km) across.
// The coverage SkyScene gives an overcast
const float OVERCAST = 0.85;
// The weather map, the slice's spline below drawn once a scene over its period, which repeats, by the clouds pass's
// mode 4, as many texels across as its size then says: read linearly, one tap in place of the spline's four, for each of
// the march's samples and those toward the light. Each texel the value in half floats and what they lose of it, added
// back as read, linear filtering keeping the sum: a half float's steps alone moved the edges of thin clouds. mapped is
// 0 where half floats can't be drawn into: the slice read as it is then
uniform sampler2D weatherMap;
uniform float mapped;

// The base shape across its slice at t, smoothly: drawn this large, a fair day's cloud is only a few of its texels
// wide, and their linear filtering cut it straight, with corners. A cubic B-spline, from four linearly filtered taps
// (Sigg and Hadwiger, "Fast Third-Order Texture Filtering", GPU Gems 2, 2005)
float smoothSlice(vec2 uv, float t) {
    vec2 texel = uv * 128.0 - 0.5;
    vec2 i = floor(texel);
    vec2 f = texel - i;
    vec2 f2 = f * f;
    vec2 f3 = f2 * f;
    vec2 w0 = (1.0 - 3.0 * f + 3.0 * f2 - f3) / 6.0;
    vec2 w1 = (4.0 - 6.0 * f2 + 3.0 * f3) / 6.0;
    vec2 w2 = (1.0 + 3.0 * f + 3.0 * f2 - 3.0 * f3) / 6.0;
    vec2 w3 = f3 / 6.0;
    vec2 g0 = w0 + w1;
    vec2 g1 = w2 + w3;
    vec2 h0 = (i - 0.5 + w1 / g0) / 128.0;
    vec2 h1 = (i + 1.5 + w3 / g1) / 128.0;
    return g0.y * (g0.x * textureLod(shapeTex, vec3(h0.x, t, h0.y), 0.0).r
                    + g1.x * textureLod(shapeTex, vec3(h1.x, t, h0.y), 0.0).r)
            + g1.y * (g0.x * textureLod(shapeTex, vec3(h0.x, t, h1.y), 0.0).r
                    + g1.x * textureLod(shapeTex, vec3(h1.x, t, h1.y), 0.0).r);
}

// What all of a pass's samples of the clouds share, worked out once a pixel by setUpSamples rather than at each of
// them: the type's height gradient, the weather map's scale, the wind's carry and the noises' shift as they change,
// and a pixel's width for each unit away
vec4 typeGradient;
float mapScale;
vec3 carried;
vec3 shapeShift;
vec3 detailShift;
float pixelWidth;

void setUpSamples() {
    typeGradient = typeGradientOf(cloudType);
    // Cut at (1 - coverage), the noise's peaks leave patches about as wide as the square root of the coverage: less
    // of it would make the clouds narrower, not fewer, and a fair day's few clouds towers and teardrops. The map is
    // drawn larger by as much, the patches as wide as an overcast's
    mapScale = 0.06 * sqrt(min(coverage / OVERCAST, 1.0));
    carried = WIND * drift + vec3(seed * 3.1, 0.0, seed * 1.7);
    shapeShift = vec3(0.0, evolve * 0.1, 0.0);
    detailShift = vec3(0.0, -evolve * 0.6, 0.0);
    pixelWidth = view.w * SPAN / size.y;
}

float weatherCoverage(vec3 p) {
    vec2 uv = vec2(p.x * mapScale + seed * 0.13, p.z * mapScale);
    vec2 m = textureLod(weatherMap, uv, 0.0).rg;
    float s = mapped > 0.5 ? m.r + m.g : smoothSlice(uv, seed * 0.07);
    return clamp(remap(s, 1.0 - coverage, 1.0, 0.0, 1.0), 0.0, 1.0);
}

// The point the noise is read at: carried by the wind, its top skewed ahead of its bottom (Nubis 2017)
vec3 windward(vec3 p, float h) {
    return p + carried + WIND * (h * SKEW);
}

// The cloud's density at a point, GPU Pro 7's sampler: the base shape times the type's height gradient, cut by the
// coverage and multiplied by it, then, unless cheap, eroded by the detail at its edges, billowy above, wispy at
// the bottom. baseDensity is the density before the erosion, Nubis's "lodded" density.
float density(vec3 p, bool cheap, out float baseDensity) {
    baseDensity = 0.0;
    float h = (p.y - CLOUD_BOTTOM) / (CLOUD_TOP - CLOUD_BOTTOM);
    // None outside the type's heights, without reading the noises: from its gradient's top up, as the coverage only
    // lowers the top the gradient is read against (h / top, top at most 1), and below its bottom as low as the
    // lowest top, a third, takes it. Nearly half the samples of an overcast fell there, most of them on the way to the
    // light
    if (h <= typeGradient.x * 0.33 || h >= typeGradient.w) {
        return 0.0;
    }
    // How wide a pixel is here, in the sky's units, for the noises' mip levels: a texel per pixel
    float footprint = length(p) * pixelWidth;
    vec3 w = windward(p, h);
    float cover = weatherCoverage(w);
    if (cover <= 0.01) {
        return 0.0;
    }
    float base = textureLod(shapeTex, w * SHAPE_SCALE + shapeShift,
            max(log2(footprint * SHAPE_SCALE * 128.0), 0.0)).r;
    // A cloud as tall as the coverage under it is strong: its top lower toward a patch's edges, so the clouds are
    // domes, small ones low, rather than columns of one height cut by the patches' outlines; by the square root, as
    // the coverage falls off toward the edges about as a square, so the tops round rather than point. Chosen: down to a third
    // of the type's height (Nubis shapes the clouds over height by the coverage too)
    float top = mix(0.33, 1.0, sqrt(cover));
    base *= h < top ? gradient(h / top, typeGradient) : 0.0;
    // The coverage as the threshold the clouds grow from as it rises (Nubis: "the clouds begin to inflate")
    float withCoverage = clamp(remap(base, 1.0 - cover, 1.0, 0.0, 1.0), 0.0, 1.0) * cover;
    baseDensity = withCoverage;
    if (cheap || withCoverage <= 0.0) {
        return withCoverage;
    }
    float detail = textureLod(detailTex, DETAIL_AXES * w * DETAIL_SCALE + detailShift,
            max(log2(footprint * DETAIL_SCALE * 32.0), 0.0)).r;
    float modifier = mix(detail, 1.0 - detail, clamp(h * 10.0, 0.0, 1.0));
    return clamp(remap(withCoverage, modifier * 0.2, 1.0, 0.0, 1.0), 0.0, 1.0);
}

// Chosen: the bolt strikes 1.5 units (2.3 km) away, near enough that the cloud base over it lies above the view, so
// that it runs down the whole sky to the horizon in front of the clouds
const float BOLT_DISTANCE = 1.5;
const int BOLT_SEGMENTS = 40;
const float BOLT_TOP = -0.12;
// Chosen from the variants shown: its core 0.8 pixels wide, its glow 0.7 as bright, four branches, a little bluish
const float BOLT_WIDTH = 0.8;
const float BOLT_GLOW = 0.7;
const int BOLT_BRANCHES = 4;
const float BOLT_BLUE = 0.2;

float boltHash(float n) {
    return fract(sin(n * 12.9898 + bolt.x * 78.233) * 43758.5453);
}

float segmentDistance(vec2 p, vec2 a, vec2 b) {
    vec2 ab = b - a;
    float h = clamp(dot(p - a, ab) / dot(ab, ab), 0.0, 1.0);
    return length(p - a - ab * h);
}

// The bolt at a point of the sky, as the sky's angles go: how near its channel, and its branches' weight. Its channel
// a jagged walk down from above the view to the horizon, each step turned at random as a leader steps on, with now
// and then a longer swerve; its branches forking off downward and fading. Returns the light of core and glow
vec2 boltLight(vec2 p) {
    float dy = (HORIZON - BOLT_TOP) / float(BOLT_SEGMENTS);
    vec2 a = vec2(bolt.z * 0.6667, BOLT_TOP);
    float best = 1e9;
    float bestBranch = 1e9;
    float branchWeight = 0.0;
    // Where each branch forks off the channel
    float forks[BOLT_BRANCHES];
    for (int k = 0; k < BOLT_BRANCHES; k++) {
        forks[k] = 6.0 + floor(boltHash(float(k) + 200.0) * 24.0);
    }
    for (int i = 0; i < BOLT_SEGMENTS; i++) {
        float fi = float(i);
        float turn = (boltHash(fi) - 0.5) * 0.03
                + step(0.85, boltHash(fi + 50.0)) * (boltHash(fi + 90.0) - 0.5) * 0.07;
        vec2 b = a + vec2(turn, dy);
        best = min(best, segmentDistance(p, a, b));
        // A branch from here, now and then
        for (int k = 0; k < BOLT_BRANCHES; k++) {
            if (abs(fi - forks[k]) > 0.5) {
                continue;
            }
            float side = boltHash(float(k) + 300.0) < 0.5 ? -1.0 : 1.0;
            vec2 c = b;
            int branchLength = 5 + int(boltHash(float(k) + 400.0) * 7.0);
            for (int j = 0; j < 12; j++) {
                if (j >= branchLength) {
                    break;
                }
                float fj = float(j);
                vec2 d = c + vec2(side * (0.006 + boltHash(fj + float(k) * 17.0 + 500.0) * 0.012)
                        + (boltHash(fj + float(k) * 13.0 + 600.0) - 0.5) * 0.016, dy * 0.8);
                float dist = segmentDistance(p, c, d);
                if (dist < bestBranch) {
                    bestBranch = dist;
                    branchWeight = 1.0 - fj / float(branchLength);
                }
                c = d;
            }
        }
        a = b;
    }
    // A pixel's width in the sky's angles, here: the core that wide, the glow wider
    float pixel = view.w / size.y;
    float width = BOLT_WIDTH * pixel;
    float core = exp(-pow(best / width, 2.0)) + 0.6 * branchWeight * exp(-pow(bestBranch / (width * 0.7), 2.0));
    float glowing = exp(-best / (width * 12.0)) * 0.35 + exp(-best / (width * 60.0)) * 0.12
            + branchWeight * exp(-bestBranch / (width * 10.0)) * 0.15;
    return vec2(core, glowing * BOLT_GLOW);
}

// How much of the light from a distance t along the ray comes through the mist: its optical depth from the ground to
// there, the fog's density integrated up the ray; t of 1e9 for beyond everything
float mistThrough(vec3 rd, float t, float extinction) {
    float up = max(rd.y, 0.005);
    float depth = extinction * MIST_HEIGHT * (1.0 - exp(-min(t * up, 50.0) / MIST_HEIGHT)) / up;
    return exp(-depth);
}

// Henyey-Greenstein
float hg(float cosAngle, float g) {
    float g2 = g * g;
    return (1.0 - g2) / pow(1.0 + g2 - 2.0 * g * cosAngle, 1.5) / (4.0 * 3.14159265);
}

// Where clouds near the horizon fade out under it
float haze(vec3 rd) {
    return smoothstep(0.02, 0.12, rd.y);
}

// The steps' offset, 0 to 1: a pixel's own random one, moved on by the golden ratio with each of its marches, so that
// its offsets over a few marches spread evenly over the step and their average comes out smooth. Random rather than
// interleaved gradient noise, whose neighbours' offsets are alike along diagonals: with a pixel marched only now and
// then, it drew stripes until the marches had averaged it out; random, it is a fine grain its neighbours smooth
float stepOffset(vec2 xy) {
    vec3 p = fract(vec3(floor(xy).xyx) * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yzx + 33.33);
    float n = fract((p.x + p.y) * p.z);
    return fract(n + 0.618034 * mod(frame, 256.0));
}

// GPU Pro 7's cone toward the light: 6 samples spreading out, the full sampler while the light has crossed little
// cloud, the cheap one after, and one far away for the shadows of distant clouds. Its turn the same for all of a
// ray's samples, set up once a pixel by setUpLight: its sine and cosine, and how far along it starts. Only those few
// kept: the cone's seven offsets held through the march took more of the GPU's registers than they saved
vec3 coneToLight;
float coneStep;
float coneCos;
float coneSin;
float coneStart;

void setUpLight(vec3 toLight, float stepLength, float turn) {
    coneToLight = toLight;
    coneStep = stepLength;
    coneCos = cos(turn);
    coneSin = sin(turn);
    coneStart = fract(turn * 0.37) - 0.5;
}

// The density along the cone from a point toward the light
float densityToLight(vec3 p) {
    // Chosen, as GPU Pro 7's kernel is not given: six fixed directions to spread the cone with, turned about the
    // vertical a little more each frame so that the frames' average smooths the cone's few samples
    const vec3 KERNEL[6] = vec3[6](vec3(0.38, -0.62, 0.69), vec3(-0.71, 0.27, -0.65), vec3(0.15, 0.92, -0.36),
            vec3(-0.53, -0.47, 0.71), vec3(0.88, 0.21, 0.43), vec3(-0.26, -0.84, -0.48));
    float c = coneCos;
    float s = coneSin;
    vec3 toLight = coneToLight;
    float stepLength = coneStep;
    float total = 0.0;
    float unused;
    vec3 q = p + toLight * stepLength * coneStart;
    for (int i = 0; i < 6; i++) {
        vec3 k = KERNEL[i];
        k = vec3(c * k.x - s * k.z, k.y, s * k.x + c * k.z);
        q += toLight * stepLength + k * stepLength * 0.5 * float(i);
        total += density(q, total > 0.3, unused);
    }
    q += toLight * stepLength * 6.0;
    total += density(q, true, unused);
    return total * stepLength;
}

#ifdef CLOUDS_PASS

// Where a point of the sky lies in the output, as gl_FragCoord counts, from a ray: ray()'s inverse
vec2 fragmentOf(vec3 d) {
    float e = atan(d.y, d.z);
    float u = d.x / length(d.yz);
    vec2 f = vec2(u / (0.6667 * SPAN) + 0.5, HORIZON - e / SPAN);
    vec2 xy = (f - view.xy) / view.zw * size;
    return vec2(xy.x, size.y - xy.y);
}

// The ray of an output pixel, as gl_FragCoord counts, and whether clouds can show there
vec3 rayAt(vec2 fragCoord, out bool clouds) {
    vec2 xy = vec2(fragCoord.x, size.y - fragCoord.y);
    vec3 rd = ray(view.xy + xy / size * view.zw);
    clouds = coverage > 0.0 && rd.y > 0.02 && veil(xy) < 0.999;
    return rd;
}

// A texture read between its pixels by a Catmull-Rom spline from nine bilinear taps, as temporal antialiasing reads its
// history: bilinear reads frame after frame, at the wind's fractions of a pixel, blurred the clouds more each frame
vec4 sharp(sampler2D tex, vec2 fragCoord, vec2 texSize) {
    vec2 texel = fragCoord - 0.5;
    vec2 base = floor(texel);
    vec2 f = texel - base;
    vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
    vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
    vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
    vec2 w3 = f * f * (-0.5 + 0.5 * f);
    vec2 w12 = w1 + w2;
    vec2 p0 = (base - 0.5) / texSize;
    vec2 p3 = (base + 2.5) / texSize;
    vec2 p12 = (base + 0.5 + w2 / w12) / texSize;
    vec4 c = vec4(0.0);
    c += texture(tex, vec2(p0.x, p0.y)) * w0.x * w0.y;
    c += texture(tex, vec2(p12.x, p0.y)) * w12.x * w0.y;
    c += texture(tex, vec2(p3.x, p0.y)) * w3.x * w0.y;
    c += texture(tex, vec2(p0.x, p12.y)) * w0.x * w12.y;
    c += texture(tex, vec2(p12.x, p12.y)) * w12.x * w12.y;
    c += texture(tex, vec2(p3.x, p12.y)) * w3.x * w12.y;
    c += texture(tex, vec2(p0.x, p3.y)) * w0.x * w3.y;
    c += texture(tex, vec2(p12.x, p3.y)) * w12.x * w3.y;
    c += texture(tex, vec2(p3.x, p3.y)) * w3.x * w3.y;
    return c;
}

// What a pixel of the clouds' frame was a frame ago: there the wind had not yet carried it, at its depth, the cloud
// base's where none was found. Past the frame's margin, which the wind does not cross between a pixel's marches, its
// edge goes on
void carriedOver(vec3 rd, vec2 cloudsCoord, out vec4 direct, out vec4 ambient) {
    vec2 cloudsSize = size + 2.0 * margin;
    float depthBefore = texture(cloudsAmbient, cloudsCoord / cloudsSize).a * DEPTH_RANGE;
    if (depthBefore <= 0.0 || depthBefore >= DEPTH_RANGE * 0.99) {
        depthBefore = CLOUD_BOTTOM / rd.y;
    }
    vec2 from = clamp(fragmentOf(rd * depthBefore + WIND * shift) + margin, vec2(0.5), cloudsSize - 0.5);
    direct = sharp(cloudsDirect, from, cloudsSize);
    ambient = sharp(cloudsAmbient, from, cloudsSize);
    // The spline overshoots a little at sharp edges: no less light or cover than none, no more cover than whole
    direct = vec4(max(direct.rgb, 0.0), clamp(direct.a, 0.0, 1.0));
    ambient = vec4(max(ambient.rgb, 0.0), clamp(ambient.a, 0.0, 1.0));
}

// The clouds along a pixel's ray: the sun's or the moon's light on them in rgb and how much of what lies behind them
// they hide in a; the sky's light on them in rgb and how far away they are in a
void march(vec3 rd, vec2 fragCoord, out vec4 outLight, out vec4 outSky) {
    vec2 xy = vec2(fragCoord.x, size.y - fragCoord.y);
    setUpSamples();
    // Toward the light: the sun's or the moon's, from straight above when neither shows, as under an overcast
    vec3 toLight = body.z > 0.5 ? ray(body.xy) : vec3(0.0, 1.0, 0.0);
    float mu = dot(rd, toLight);
    // Nubis 2017's directional scattering: two Henyey-Greenstein lobes, the second the silver lining round the light
    float phase = max(hg(mu, ECCENTRICITY), SILVER_INTENSITY * hg(mu, 0.99 - SILVER_SPREAD));
    // Horizon: rain clouds darkened by more absorption; chosen: up to twice as much
    float absorption = 1.0 + precipitation;
    float t0 = CLOUD_BOTTOM / rd.y;
    float t1 = min(CLOUD_TOP / rd.y, t0 + 12.0);
    // Horizon: more samples the longer the ray through the layer, from 64 overhead to 128 toward the horizon; here
    // the steps' share of them, the marches averaged. The cheap ones are as long: twice as long, as Horizon's, they
    // leapt over the thin edges of clouds this near, in combs
    float dt = (t1 - t0) / (steps * mix(1.0, 2.0, 1.0 - rd.y));
    float t = t0 + dt * stepOffset(xy);
    float transmit = 1.0;
    vec3 direct = vec3(0.0);
    vec3 skyLight = vec3(0.0);
    float depthSum = 0.0;
    float weightSum = 0.0;
    // Horizon's march: cheap samples at long steps until one finds cloud, then a step back and full samples; after
    // several empty full samples, cheap again
    bool fine = false;
    int empty = 0;
    setUpLight(toLight, (CLOUD_TOP - CLOUD_BOTTOM) / 6.0 * 0.5, 6.2831853 * stepOffset(xy + 17.0));
    // The type's top: the ray rises, so above it there is no more cloud
    float topY = CLOUD_BOTTOM + typeGradient.w * (CLOUD_TOP - CLOUD_BOTTOM);
    // Horizon: the sky's light grows over the clouds' height. At their bottoms it comes from the sky around them, open
    // as much as the coverage leaves it, and from the ground below; chosen, the ground's 0.45 of it, the bottoms of a
    // closed deck getting that
    float underSky = mix(1.0, 0.45, coverage);
    // The light's cone, the most of the march's cost, taken only on every second step: a sample in cloud on another is
    // lit as by the average depth toward the light of those taken either side of it, its share kept until the next is
    // taken, or lit as the one before if none comes. By the step's number, the same for all the rays the GPU marches
    // together, so that they all skip the cone at once; by each ray's own count of its samples in cloud, half of them
    // wanted it on every step and none was skipped. The even steps one march, the odd ones the next, so that the
    // averaged marches cancel what the guesses miss, which drew faint bands along the steps' depths. The second
    // Beer-Lambert's ramp is the ray's own
    int coneParity = int(mod(frame, 2.0));
    float ramp = 0.7 * (1.0 - smoothstep(0.6, 0.95, mu));
    float lastDepth = 0.0;
    bool known = false;
    float waiting = 0.0;
    vec3 waitingAt = vec3(0.0);
    for (int i = 0; i < 256; i++) {
        if (t > t1 || transmit < 0.02) {
            break;
        }
        vec3 p = rd * t;
        if (p.y >= topY) {
            break;
        }
        float lodded;
        if (!fine) {
            if (density(p, true, lodded) > 0.0) {
                fine = true;
                // Back a step, so the cloud's near edge is not skipped
                t = max(t - dt, t0);
                empty = 0;
                continue;
            }
            t += dt;
            continue;
        }
        float d = density(p, false, lodded);
        if (d > 0.0) {
            empty = 0;
            float h = clamp((p.y - CLOUD_BOTTOM) / (CLOUD_TOP - CLOUD_BOTTOM), 0.0, 1.0);
            // Nubis 2017's in-scatter probability: fewer rays scatter into a cloud's thin edges and its base
            float depthProbability = 0.05 + pow(lodded, clamp(remap(h, 0.3, 0.85, 0.5, 2.0), 0.5, 2.0));
            float verticalProbability = pow(clamp(remap(h, 0.07, 0.14, 0.1, 1.0), 0.1, 1.0), 0.8);
            float inScatter = depthProbability * verticalProbability;
            float a = 1.0 - exp(-sigma * d * dt);
            // Its share of the light, before how much of it reaches here
            float share = transmit * a * inScatter;
            if ((i & 1) == coneParity) {
                float lightDepth = densityToLight(p) * sigma * absorption;
                // Nubis 2017's absorption and out-scatter, with light pushed further in by a second, gentler
                // Beer-Lambert; ramped down looking toward the light (chosen ramp)
                float lightShare = share * max(exp(-lightDepth), exp(-lightDepth * 0.25) * ramp);
                if (waiting > 0.0) {
                    float between = known ? 0.5 * (lastDepth + lightDepth) : lightDepth;
                    lightShare += waiting * max(exp(-between), exp(-between * 0.25) * ramp);
                    waiting = 0.0;
                }
                // Horizon: the sun's light directly, the sky's over height; apart, as lightning lights them unlike
                direct += lightShare * light * phase * 4.0 * 3.14159265;
                lastDepth = lightDepth;
                known = true;
            } else {
                if (!known && waiting <= 0.0) {
                    waitingAt = p;
                }
                waiting += share;
            }
            skyLight += transmit * a * mix(ambient * underSky, ambientTop, h);
            depthSum += transmit * a * t;
            weightSum += transmit * a;
            transmit *= 1.0 - a;
        } else if (++empty >= 6) {
            fine = false;
        }
        t += dt;
    }
    if (waiting > 0.0) {
        // The last samples in cloud lit as the one before them, or, none taken, by the cone from the first of them
        float depth = known ? lastDepth : densityToLight(waitingAt) * sigma * absorption;
        direct += waiting * max(exp(-depth), exp(-depth * 0.25) * ramp) * light * phase * 4.0 * 3.14159265;
    }
    outLight = vec4(direct, 1.0 - transmit);
    outSky = vec4(skyLight, (weightSum > 0.0 ? depthSum / weightSum : t1) / DEPTH_RANGE);
}

const vec4 NO_LIGHT = vec4(0.0);
const vec4 NO_SKY = vec4(0.0, 0.0, 0.0, 1.0);

void main() {
    if (mode == 4.0) {
        // The slice at each of the map's texels' middles, as linear filtering reads them
        float s = smoothSlice(gl_FragCoord.xy / size, seed * 0.07);
        float high = unpackHalf2x16(packHalf2x16(vec2(s))).x;
        outDirect = vec4(high, s - high, 0.0, 0.0);
        outAmbient = NO_SKY;
        return;
    }
    if (mode == 1.0) {
        // A quarter of the size: the ray of the block's pixel whose turn it is, marched as it is
        vec2 fragCoord = floor(gl_FragCoord.xy) * 4.0 + turnCell + 0.5 - margin;
        bool clouds;
        vec3 rd = rayAt(fragCoord, clouds);
        if (clouds) {
            march(rd, fragCoord, outDirect, outAmbient);
        } else {
            outDirect = NO_LIGHT;
            outAmbient = NO_SKY;
        }
        return;
    }
    if (mode == 3.0) {
        // A march at each block's middle: between them, as a texel's middle lies at its block's
        vec2 marchedSize = vec2(textureSize(marchedDirect, 0));
        vec2 uv = gl_FragCoord.xy / 4.0 / marchedSize;
        outDirect = texture(marchedDirect, uv);
        outAmbient = texture(marchedAmbient, uv);
        return;
    }
    // The clouds' frame's pixel in the output's pixels, past its edges in the margin
    vec2 fragCoord = gl_FragCoord.xy - margin;
    bool clouds;
    vec3 rd = rayAt(fragCoord, clouds);
    if (!clouds) {
        // No clouds here, nor ever
        outDirect = NO_LIGHT;
        outAmbient = NO_SKY;
        return;
    }
    vec4 directBefore = NO_LIGHT;
    vec4 ambientBefore = NO_SKY;
    if (weight < 1.0) {
        carriedOver(rd, gl_FragCoord.xy, directBefore, ambientBefore);
    }
    vec4 newDirect;
    vec4 newAmbient;
    if (mode == 0.0) {
        march(rd, fragCoord, newDirect, newAmbient);
    } else {
        ivec2 pixel = ivec2(gl_FragCoord.xy);
        if (weight <= 0.0 || !all(equal(pixel % 4, ivec2(turnCell)))) {
            if (weight >= 1.0) {
                carriedOver(rd, gl_FragCoord.xy, directBefore, ambientBefore);
            }
            outDirect = directBefore;
            outAmbient = ambientBefore;
            return;
        }
        // Its own march, at a quarter of the size
        newDirect = texelFetch(marchedDirect, pixel / 4, 0);
        newAmbient = texelFetch(marchedAmbient, pixel / 4, 0);
    }
    outDirect = weight >= 1.0 ? newDirect : mix(directBefore, newDirect, weight);
    outAmbient = weight >= 1.0 ? newAmbient : mix(ambientBefore, newAmbient, weight);
}

#else

void main() {
    if (boltPass > 0.5) {
        // The bolt at the pixel of the output's top half, as the sky's pixel below reads it
        vec2 f = view.xy + vec2(gl_FragCoord.x, size.y - gl_FragCoord.y) / size * view.zw;
        outColor = vec4(boltLight(vec2(f.x * 0.6667, f.y)), 0.0, 1.0);
        return;
    }
    // The top half of the output the sky, the bottom half how far the night can be seen; GL counts rows from the
    // bottom, so the top half is the upper rows
    bool seeThroughHalf = gl_FragCoord.y < size.y;
    vec2 at = vec2(gl_FragCoord.x, seeThroughHalf ? gl_FragCoord.y : gl_FragCoord.y - size.y);
    // From the top, as the views count
    vec2 xy = vec2(at.x, size.y - at.y);
    vec2 f = view.xy + xy / size * view.zw;
    vec3 rd = ray(f);
    float thin = veil(xy);
    vec2 cloudsSize = size + 2.0 * margin;
    vec2 cloudsAt = (at + margin) / cloudsSize;
    vec4 cloudsHere = texture(cloudsDirect, cloudsAt);
    vec4 skyLightHere = texture(cloudsAmbient, cloudsAt);
    if (settled < 1.0) {
        // Over a 4x4 block's reach, from nine bilinear reads 1.5 pixels apart: the pixels marched at different turns
        // and the steps' offsets averaged out
        vec4 direct = vec4(0.0);
        vec4 skyLight = vec4(0.0);
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                vec2 uv = cloudsAt + vec2(float(x), float(y)) * 1.5 / cloudsSize;
                float w = (x == 0 ? 2.0 : 1.0) * (y == 0 ? 2.0 : 1.0) / 16.0;
                direct += texture(cloudsDirect, uv) * w;
                skyLight += texture(cloudsAmbient, uv) * w;
            }
        }
        cloudsHere = mix(direct, cloudsHere, settled);
        skyLightHere = mix(skyLight, skyLightHere, settled);
    }
    float fade = haze(rd) * (1.0 - thin);
    float fogExtinction = 0.0;
    if (mist > 0.0) {
        float drifting = fbm(vec3(f.x * 3.0 + drift * 0.6, f.y * 6.0, evolve * 2.5));
        fogExtinction = mist * MIST_EXTINCTION * (0.8 + 0.4 * drifting);
    }
    float throughAll = mist > 0.0 ? mistThrough(rd, 1e9, fogExtinction) : 1.0;
    if (seeThroughHalf) {
        // Through the clouds, though not where they thin under the texts, and through the mist
        float seeThrough = 1.0 - cloudsHere.a * haze(rd);
        float s = mix(seeThrough, 1.0, thin) * throughAll;
        outColor = vec4(s, s, s, 1.0);
        return;
    }

    // As seen this low, the sky's colour climbs from the horizon's to the zenith's within the view
    vec3 col = mix(horizon, zenith, pow(clamp(rd.y * 2.4, 0.0, 1.0), 0.7));
    // The sun's disc, added last at full brightness, as much as the clouds let it through
    float sunDisc = 0.0;
    if (body.z > 0.5) {
        vec3 bd = ray(body.xy);
        float mu = dot(rd, bd);
        float m = max(mu, 0.0);
        col += light * glow * (0.03 * pow(m, 8.0) + 0.25 * pow(m, 60.0));
        if (body.z < 1.5) {
            sunDisc = smoothstep(0.99955, 0.99975, mu);
        }
        // The moon's disc is drawn over it all at full size, hidden by the clouds as they are thick: sky_moon.agsl
        // over the live sky, sky_overlay.frag over the stills
    }

    if (stars > 0.0) {
        // A faint milky band across the night, its patches as wide as high; its stars are sky_stars'
        float across = (rd.x * 1.2 + rd.z * 0.35 - 0.25) / 0.18;
        float band = exp(-across * across) * fbm(vec3(rd.x * 9.0, rd.y * 9.0, 2.0));
        col += vec3(0.35, 0.38, 0.55) * 0.18 * band * stars * smoothstep(0.02, 0.2, rd.y) * (1.0 - 0.5 * thin);
    }

    // Under the texts over the sky the clouds thin out into their own even colour, the sky's where it is clear: the
    // cards below show the sky's mood, not its clouds
    vec3 lowSky = mix(col, light * 0.3 + ambient * 0.62, smoothstep(0.35, 0.95, coverage));

    // The clouds as the clouds pass left them, lightning lighting them, as the sun's light 2.5 times more and the
    // sky's once more; Horizon: attenuated to the atmosphere's colour over their depth, chosen rate
    vec3 cloudLight = cloudsHere.rgb * (1.0 + 2.5 * flash) + skyLightHere.rgb * (1.0 + flash);
    float cover = cloudsHere.a;
    float depth = skyLightHere.a * DEPTH_RANGE;
    vec3 airLight = mix(horizon, vec3(0.85, 0.88, 0.92), 0.35);
    float air = 1.0 - exp(-max(depth - 2.0, 0.0) * 0.06);
    cloudLight = mix(cloudLight, airLight * cover, air) * fade;
    float cloudCover = cover * fade;
    vec3 sky = col;
    col = col * (1.0 - cloudCover) + cloudLight;
    sunDisc *= 1.0 - cloudCover;
    if (mist > 0.0) {
        // In front of the clouds the mist's own light, behind them more of it and the sky dimmed through it all.
        // Its light the day's or the night's on a white veil, chosen; its thickness drifting a little
        vec3 fog = light * 0.45 + ambient * 0.55;
        float toClouds = mistThrough(rd, cloudCover > 0.0 ? depth : 1e9, fogExtinction);
        col = fog * (1.0 - toClouds) + cloudLight * toClouds
                + (1.0 - cloudCover) * (fog * (toClouds - throughAll) + sky * throughAll);
        lowSky = mix(fog, lowSky, throughAll);
        sunDisc *= throughAll;
    }
    if (bolt.y > 0.0) {
        // The bolt in front of the clouds, dimmed by the mist up to it
        vec2 strike = boltMapped > 0.5 ? texelFetch(boltMap, ivec2(at), 0).rg : boltLight(vec2(f.x * 0.6667, f.y));
        float seen = bolt.y * (mist > 0.0 ? mistThrough(rd, BOLT_DISTANCE, mist * MIST_EXTINCTION) : 1.0);
        vec3 glowColor = mix(vec3(0.85, 0.85, 1.0), vec3(0.55, 0.62, 1.0), BOLT_BLUE);
        col += (vec3(1.0, 0.98, 1.0) * strike.x * 1.4 + glowColor * strike.y) * seen;
    }
    col = mix(col, lowSky, thin);
    sunDisc *= 1.0 - thin;
    col += vec3(0.85, 0.88, 1.0) * 0.18 * flash;

    // Muted, as a photograph: less saturated, a little more contrast, though not where it is dark, as at night
    float lum = dot(col, vec3(0.2126, 0.7152, 0.0722));
    col = mix(vec3(lum), col, 0.85);
    col = mix(col, (col - 0.5) * 1.12 + vec3(0.5, 0.5, 0.48), smoothstep(0.08, 0.35, lum));
    // Soft highlights, short of white so that white text stays readable over the brightest cloud
    col = mix(col, 0.72 + 0.16 * (1.0 - exp(-(col - 0.72) * 5.0)), step(0.72, col));
    col = mix(col, vec3(1.0, 0.98, 0.94), sunDisc);
    // A dither against banding
    float n = fract(sin(dot(floor(gl_FragCoord.xy), vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
    outColor = vec4(clamp(col + n / 255.0, 0.0, 1.0), 1.0);
}

#endif
