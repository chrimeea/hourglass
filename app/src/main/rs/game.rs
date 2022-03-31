#pragma version(1)
#pragma rs java_package_name(com.prozium.hourglass)
#pragma rs_fp_relaxed

#define ATTENUATION 0.5
float width, height, scale, width_pixel, height_pixel;
int32_t total = 0, width_scales, total_scales;
short *buckets_start, *buckets_list, *buckets_iteration, iteration = 1;
float2 *forces, *next_forces, *pos, *next_pos, *hit, pinch, safe, scale_to_pixels;
float4 *quads, *color;
bool* background;

#define buckets_index(P) (((int32_t) (((P).x + width) / scale)) + ((int32_t) (((P).y + height) / scale)) * width_scales)
#define buckets_start_for_iteration(B) (buckets_iteration[B] == iteration ? buckets_start[B] : -1)

static float2 hit_direction(float2 n, const float2 c) {
    float i, j, a, b;
    float2 k, pas, v, t, r = n;
    if (n.x > width) {
        n.x = width;
    } else if (n.x < -width) {
        n.x = -width;
    }
    if (n.y > height) {
        n.y = height;
    } else if (n.y < -height) {
        n.y = -height;
    }
    i = round((float) ((n.x + width) * (width_pixel - 1.0) / (2.0 * width)));
    j = round((float) ((n.y + height) * (height_pixel - 1.0) / (2.0 * height)));
    if (!background[(int32_t) (i + j * width_pixel)]) {
        a = round((float) ((c.x + width) * (width_pixel - 1.0) / (2.0 * width)));
        b = round((float) ((c.y + height) * (height_pixel - 1.0) / (2.0 * height)));
        t.x = i - a;
        t.y = j - b;
        if (fabs(t.x) < fabs(t.y)) {
            pas.x = t.x / fabs(t.y);
            pas.y = sign(t.y);
        } else {
            pas.x = sign(t.x);
            pas.y = t.y / fabs(t.x);
        }
        for (k = (float2) {a, b}; i != round(k.x) || j != round(k.y); k += pas) {
            v = hit[(int32_t) k.x + (int32_t) k.y * (int32_t) width_pixel];
            if (v.x != 0.0 || v.y != 0.0) {
                return v + ((float2) {k.x * (2.0 * width) / (width_pixel - 1.0) - width, k.y * (2.0 * height) / (height_pixel - 1.0) - height} - r);
            }
        }
        return c - r;
    } else {
        return hit[(int32_t) i + (int32_t) j * (int32_t) width_pixel] + (n - r);
    }
}

static bool is_in_array(const float2 *a, const int32_t t, const float2 v) {
    int32_t i;
    for (i = 0; i < t; i++) {
        if (distance(a[i], v) < scale / 2.0) {
            return true;
        }
    }
    return false;
}

void func_init_pos(const int32_t t) {
    int32_t i, j, s = 1;
    bool added;
    float x, y, k, l;
    float2 seed[t], n;
    float4 c1 = (float4) {rsRand(256), rsRand(256), rsRand(256), 255.0} / 255.0;
    float4 c2 = (float4) {rsRand(256), rsRand(256), rsRand(256), 255.0} / 255.0;
    seed[0] = safe;
    for (i = 0; i < t && s > 0; i++) {
        j = rsRand(s);
        pos[total] = forces[total] = seed[j];
        color[6 * total] = (i < t / 2.0 ? c1 : c2);
        color[6 * total + 1] = color[6 * total];
        color[6 * total + 2] = color[6 * total];
        color[6 * total + 3] = color[6 * total];
        color[6 * total + 4] = color[6 * total];
        color[6 * total + 5] = color[6 * total];
        total++;
        added = false;
        for (k = -1.0; k <= 1.0 && s < t; k++) {
            for (l = -1.0; l <= 1.0 && s < t; l++) {
                if (k != 0.0 || l != 0.0) {
                    n = seed[j] + ((float2) {k, l}) * scale;
                    if (fabs(n.x) < width && fabs(n.y) < height && buckets_start[buckets_index(n)] != 1 && !is_in_array(seed, s, n) && !is_in_array(pos, total, n)) {
                        seed[s++] = n;
                        added = true;
                    }
                }
            }
        }
        if (added) {
            seed[j] = seed[s - 1];
        } else {
            for (j++; j < s; j++) {
                seed[j - 1] = seed[j];
            }
        }
        s--;
    }
}

void func_make_buckets() {
    int32_t b, i;
    iteration++;
    for (i = 0; i < total; i++) {
        pos[i] += pinch;
        b = buckets_index(pos[i]);
        if (buckets_iteration[b] == iteration) {
            buckets_list[i] = buckets_start[b];
        } else {
            buckets_list[i] = -1;
            buckets_iteration[b] = iteration;
        }
        buckets_start[b] = i;
    }
}

static int32_t vecinity(const float2 n, int32_t *b) {
    int32_t k, m;
    m = 1;
    k = buckets_index(n);
    b[0] = buckets_start_for_iteration(k);
    if (k % width_scales < width_scales - 1.0) {
        b[m++] = buckets_start_for_iteration(k + 1);
    }
    if (k % width_scales < width_scales - 1 && k + width_scales < total_scales) {
        b[m++] = buckets_start_for_iteration(k + 1 + width_scales);
    }
    if (k + width_scales < total_scales) {
        b[m++] = buckets_start_for_iteration(k + width_scales);
    }
    if (k % width_scales > 0  && k + width_scales < total_scales) {
        b[m++] = buckets_start_for_iteration(k - 1 + width_scales);
    }
    if (k % width_scales > 0) {
        b[m++] = buckets_start_for_iteration(k - 1);
    }
    if (k % width_scales > 0 && k >= width_scales) {
        b[m++] = buckets_start_for_iteration(k - 1 - width_scales);
    }
    if (k >= width_scales) {
        b[m++] = buckets_start_for_iteration(k - width_scales);
    }
    if (k % width_scales < width_scales - 1 && k >= width_scales) {
        b[m++] = buckets_start_for_iteration(k + 1 - width_scales);
    }
    return m;
}

static void collide(const uint32_t x) {
    int32_t i, j, b[9], m;
    float d;
    float2 t;
    m = vecinity(pos[x], b);
    for (j = 0; j < m; j++) {
        i = b[j];
        while (i != -1) {
            t = pos[x] - pos[i];
            d = distance(pos[x], pos[i]);
            if (d < scale && d > 0.0) {
                next_pos[x] -= t * 0.5 * (d - scale) / d;
            }
            i = buckets_list[i];
        }
    }
}

static void collide2(const uint32_t x) {
    int32_t i, j, b[9], m;
    float d;
    float2 v1, v2, t;
    m = vecinity(pos[x], b);
    for (j = 0; j < m; j++) {
        i = b[j];
        while (i != -1) {
            t = next_pos[x] - next_pos[i];
            d = distance(next_pos[x], next_pos[i]);
            if (d < scale && d > 0.0) {
                v1 = pos[x] - forces[x];
                v2 = next_pos[i] - next_forces[i];
                pos[x] -= t * 0.5 * (d - scale) / d;
                v1 += t * ATTENUATION * (dot(t, v2) - dot(t, v1)) / (d * d);
                forces[x] = pos[x] - v1;
            }
            i = buckets_list[i];
        }
    }
}

void root_resolve_to_next_pos(const float2 *in, uint32_t x) {
    float2 temp;
    next_pos[x] = pos[x];
    next_forces[x] = forces[x];
    collide(x);
    temp = next_pos[x];
    next_pos[x] += hit_direction(next_pos[x], pos[x]);
    temp = 2.0 * next_pos[x] - next_forces[x];
    next_forces[x] = next_pos[x];
    next_pos[x] = temp;
}

void root_resolve_to_pos(const float2 *in, uint32_t x) {
    float2 temp, old_pos = pos[x];
    pos[x] = next_pos[x];
    forces[x] = next_forces[x];
    collide2(x);
    temp = pos[x];
    pos[x] += hit_direction(pos[x], old_pos);
    /*if (temp.x != pos[x].x || temp.y != pos[x].y) {
        forces[x] = pos[x] - (forces[x] - temp) * ATTENUATION;
    }*/
}

void root_make_quads(const float2 *in, uint32_t x) {
    quads[6 * x] = (float4) {in->x - scale, in->y + scale, 0.0, 1.0};
    quads[6 * x + 1] = (float4) {in->x - scale, in->y - scale, 0.0, 0.0};
    quads[6 * x + 2] = (float4) {in->x + scale, in->y - scale, 1.0, 0.0};
    quads[6 * x + 3] = (float4) {in->x - scale, in->y + scale, 0.0, 1.0};
    quads[6 * x + 4] = (float4) {in->x + scale, in->y - scale, 1.0, 0.0};
    quads[6 * x + 5] = (float4) {in->x + scale, in->y + scale, 1.0, 1.0};
}

void root_init_hit(const bool *in, uint32_t x) {
    uint32_t a, b;
    int32_t i, j, e;
    float t, d;
    float2 c, o;
    t = 0.0;
    hit[x] = 0.0;
    if (*in) {
        a = x % (int32_t) width_pixel;
        b = x / width_pixel;
        c.x = a * (2.0 * width) / (width_pixel - 1.0) - width;
        c.y = b * (2.0 * height) / (height_pixel - 1.0) - height;
        e = buckets_index(c);
        if (buckets_start[e] == 1
                   || (e % width_scales < width_scales - 1 && buckets_start[e + 1] == 1)
                   || (e % width_scales < width_scales - 1 && e + width_scales < total_scales && buckets_start[e + width_scales + 1] == 1)
                   || (e + width_scales < total_scales && buckets_start[e + width_scales] == 1)
                   || (e % width_scales > 0 && e + width_scales < total_scales && buckets_start[e + width_scales - 1] == 1)
                   || (e % width_scales > 0 && buckets_start[e - 1] == 1)
                   || (e % width_scales > 0 && e >= width_scales && buckets_start[e - width_scales - 1] == 1)
                   || (e >= width_scales && buckets_start[e - width_scales] == 1)
                   || (e % width_scales < width_scales - 1 && e >= width_scales && buckets_start[e - width_scales + 1] == 1)) {
            for (i = max((int32_t) (a - scale_to_pixels.x / 2.0), 0); i < min((int32_t) (a + scale_to_pixels.x / 2.0), (int32_t) width_pixel); i++) {
                for (j = max((int32_t) (b - scale_to_pixels.y / 2.0), 0); j < min((int32_t) (b + scale_to_pixels.y / 2.0), (int32_t) height_pixel); j++) {
                    if (!background[(int32_t) (i + j * width_pixel)]) {
                        o.x = i * (2.0 * width) / (width_pixel - 1.0) - width;
                        o.y = j * (2.0 * height) / (height_pixel - 1.0) - height;
                        d = distance(c, o);
                        if (d < scale / 2.0 && d > 0.0) {
                            hit[x] += (c - o) * (scale / 2.0 - d) / d;
                            t++;
                        }
                    }
                }
            }
            if (t > 1.0) {
                hit[x] /= t;
            }
        }
    }
}

void func_init_safe() {
    float i, j, k, l;
    int32_t b = 0;
    width_scales = (2.0 * width) / scale;
    if ((float) width_scales < (2.0 * width) / scale) {
        width_scales++;
    }
    safe = (float2) {width_pixel - 1, height_pixel - 1};
    for (j = 0.0; j < height_pixel; j += scale_to_pixels.y) {
        for (i = 0.0; i < width_pixel; i += scale_to_pixels.x) {
            for (l = j; l < min(j + scale_to_pixels.y, height_pixel); l++) {
                for (k = i; k < min(i + scale_to_pixels.x, width_pixel); k++) {
                    if (!background[(int32_t) (k + l * width_pixel)]) {
                        buckets_start[b] = 1;
                        l = height_pixel;
                        break;
                    }
                }
            }
            if (l < height_pixel && safe.y > j) {
                safe = (float2) {i, j};
            }
            b++;
        }
    }
    total_scales = b;
    safe.x = safe.x * (2.0 * width) / (width_pixel - 1.0) - width + scale / 2.0;
    safe.y = safe.y * (2.0 * height) / (height_pixel - 1.0) - height + scale / 2.0;
}

void root_init_background(const uchar4 *in, uint32_t x, uint32_t y) {
    background[(int32_t) (x + y * width_pixel)] = (in->w != 0);
}
