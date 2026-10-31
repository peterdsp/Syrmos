"""Composite a real Syrmos capture into a supplied device mockup.

Two modes:
  * quad   - perspective-map the capture onto four corner points (a tilted panel)
  * hole   - the mockup has a transparent screen cutout; the capture goes behind it
Nothing is ever stretched off its own aspect: the capture is scaled to COVER the
target and centre-cropped, and the crop percentage is reported so it can be audited.
"""
from PIL import Image
import numpy as np, json, sys

def _cover(img, tw, th):
    """Scale to cover tw x th at the capture's own aspect, centre-crop the rest."""
    sw, sh = img.size
    s = max(tw/sw, th/sh)
    nw, nh = round(sw*s), round(sh*s)
    img = img.resize((nw, nh), Image.LANCZOS)
    x = (nw-tw)//2; y = (nh-th)//2
    crop_pct = (1 - (tw*th)/(nw*nh)) * 100
    return img.crop((x, y, x+tw, y+th)), crop_pct

def _homography(src, dst):
    A = []
    for (x,y),(u,v) in zip(src, dst):
        A.append([x,y,1,0,0,0,-u*x,-u*y,-u])
        A.append([0,0,0,x,y,1,-v*x,-v*y,-v])
    _,_,V = np.linalg.svd(np.array(A, float))
    H = V[-1].reshape(3,3)
    return H/H[2,2]

def detect_screen(mockup, sat_min=0.085, val_min=0.30, bbox=None, erode=2):
    """Mask of the mockup's real lit screen.

    The wallpaper is the only saturated bright region; the body is neutral silver and
    the backdrop neutral grey. Each row is filled between its first and last screen
    pixel, so desaturated artwork inside the screen cannot punch holes in the mask.
    This follows the screen's true outline, including the curved bottom at the crease
    and the slanted right edge that a four-point quad cannot represent.
    """
    import numpy as _np
    from PIL import ImageFilter
    a = _np.asarray(Image.open(mockup).convert('RGB')).astype(float)
    mx = a.max(axis=2); mn = a.min(axis=2)
    sat = _np.where(mx > 0, (mx - mn) / _np.maximum(mx, 1), 0)
    m = (sat > sat_min) & (mx / 255.0 > val_min)
    if bbox:
        x0, y0, x1, y1 = bbox
        keep = _np.zeros_like(m); keep[y0:y1, x0:x1] = True
        m &= keep
    filled = _np.zeros_like(m)
    for y in range(m.shape[0]):
        xs = _np.where(m[y])[0]
        if len(xs) >= 8:
            filled[y, xs.min():xs.max() + 1] = True
    img = Image.fromarray((filled * 255).astype('uint8'))
    if erode:
        img = img.filter(ImageFilter.MinFilter(erode * 2 + 1))
    return img


def quad(mockup, capture, corners, out, feather=1.2, corner_frac=55/669,
         round_corners=('tl','tr','br','bl'), screen_mask=None):
    mk = Image.open(mockup).convert('RGBA')
    cap = Image.open(capture).convert('RGBA')
    tl,tr,br,bl = [tuple(map(float,c)) for c in corners]
    # The quad IS the projection of the screen rectangle, so the homography
    # already carries the foreshortening. Map the WHOLE capture onto it: no crop,
    # and no squash beyond the perspective the mockup itself depicts.
    w, h = cap.size
    crop_pct = 0.0
    # PIL wants the INVERSE map (dst -> src)
    H = _homography([tl,tr,br,bl], [(0,0),(w,0),(w,h),(0,h)])
    warped = cap.transform(mk.size, Image.PERSPECTIVE, H.flatten()[:8],
                           resample=Image.BICUBIC)
    # Mask built in SOURCE space as a rounded rectangle at the display's real
    # corner radius, then warped by the same homography, so the corners project
    # correctly instead of being cut square.
    from PIL import ImageDraw, ImageFilter
    src_mask = Image.new('L', (w, h), 0)
    r = round(min(w, h) * corner_frac)
    dd = ImageDraw.Draw(src_mask)
    if r > 0:
        dd.rounded_rectangle([0, 0, w-1, h-1], radius=r,
                             corners=tuple(c in round_corners for c in ('tl','tr','br','bl')),
                             fill=255)
    else:
        dd.rectangle([0, 0, w-1, h-1], fill=255)
    mask = src_mask.transform(mk.size, Image.PERSPECTIVE, H.flatten()[:8],
                              resample=Image.BICUBIC)
    if screen_mask is not None:
        # Union of the warped rounded rectangle and the mockup's own detected screen.
        # The geometric half covers desaturated artwork (pale sky) that colour
        # detection misses; the detected half covers the curved bottom at the crease
        # that no four-point quad can follow.
        import numpy as _np
        mask = Image.fromarray(_np.maximum(_np.asarray(mask),
                                           _np.asarray(screen_mask)).astype('uint8'))
    mask = mask.filter(ImageFilter.GaussianBlur(feather))
    out_im = mk.copy()
    out_im.paste(warped, (0,0), mask)
    # carry the mockup's own specular highlight back over the new screen, so the
    # glass still reads as glass instead of a flat sticker
    import numpy as _np
    g = mk.convert('L')
    near = _np.asarray(g.filter(ImageFilter.GaussianBlur(35)), float)
    far  = _np.asarray(g.filter(ImageFilter.GaussianBlur(160)), float)
    # low-frequency only: broad glare survives, the mockup's own artwork does not
    spec = _np.clip(near - far, 0, None) / 255.0
    m = _np.asarray(mask, float)/255.0
    cur = _np.asarray(out_im.convert('RGB'), float)
    lift = (spec * m)[..., None] * 255.0 * 1.15
    cur = _np.clip(cur + lift, 0, 255).astype('uint8')
    out_im = Image.fromarray(cur).convert('RGBA')
    out_im.save(out)
    return dict(mode='quad', size=mk.size, screen=[w,h], crop_pct=round(crop_pct,3))

def hole(mockup, capture, rect, out):
    """Mockup is a frame with a transparent screen cutout; capture sits behind it."""
    mk = Image.open(mockup).convert('RGBA')
    cap = Image.open(capture).convert('RGBA')
    x0,y0,x1,y1 = rect
    w,h = x1-x0+1, y1-y0+1
    cap, crop_pct = _cover(cap, w, h)
    base = Image.new('RGBA', mk.size, (0,0,0,0))
    base.paste(cap, (x0,y0))
    out_im = Image.alpha_composite(base, mk)
    out_im.save(out)
    return dict(mode='hole', size=mk.size, screen=[w,h], crop_pct=round(crop_pct,3))

def rect_paste(mockup, capture, rect, out):
    """Opaque mockup with a flat rectangular screen: paste the capture over it."""
    mk = Image.open(mockup).convert('RGBA')
    cap = Image.open(capture).convert('RGBA')
    x0,y0,x1,y1 = rect
    w,h = x1-x0+1, y1-y0+1
    cap, crop_pct = _cover(cap, w, h)
    mk.paste(cap, (x0,y0))
    mk.save(out)
    return dict(mode='rect', size=mk.size, screen=[w,h], crop_pct=round(crop_pct,3))

if __name__ == '__main__':
    print(json.dumps(globals()[sys.argv[1]](*json.loads(sys.argv[2])), indent=1))


def knockout(path, out, tol=26, feather=1.0, clear_below=None, clear_above=None,
             clear_outside=None):
    """Flood the flat studio backdrop from the image border into alpha.

    Only pixels REACHABLE from the border within `tol` of the border colour are
    removed, so same-coloured pixels inside the device survive.
    """
    from PIL import ImageFilter
    import numpy as _np
    from collections import deque as _dq
    im = Image.open(path).convert('RGB')
    a = _np.asarray(im).astype(int)
    H, W, _ = a.shape
    border = _np.concatenate([a[0, :], a[-1, :], a[:, 0], a[:, -1]])
    bg = _np.median(border, axis=0)
    dist = _np.abs(a - bg).sum(axis=2)
    cand = dist <= tol
    out_mask = _np.zeros((H, W), bool)
    q = _dq()
    for x in range(W):
        for y in (0, H - 1):
            if cand[y, x] and not out_mask[y, x]:
                out_mask[y, x] = True; q.append((y, x))
    for y in range(H):
        for x in (0, W - 1):
            if cand[y, x] and not out_mask[y, x]:
                out_mask[y, x] = True; q.append((y, x))
    while q:
        y, x = q.popleft()
        for dy, dx in ((1,0),(-1,0),(0,1),(0,-1)):
            ny, nx = y+dy, x+dx
            if 0 <= ny < H and 0 <= nx < W and cand[ny, nx] and not out_mask[ny, nx]:
                out_mask[ny, nx] = True; q.append((ny, nx))
    alpha = _np.where(out_mask, 0, 255).astype('uint8')
    # the studio contact shadow is darker than the backdrop, so the flood stops at
    # it; drop it explicitly, the films cast their own shadow
    if clear_below is not None: alpha[int(clear_below):, :] = 0
    if clear_above is not None: alpha[:int(clear_above), :] = 0
    if clear_outside is not None:
        x0, y0, x1, y1 = [int(v) for v in clear_outside]
        keep = _np.zeros_like(alpha); keep[y0:y1, x0:x1] = 1
        alpha = (alpha * keep).astype('uint8')
    am = Image.fromarray(alpha).filter(ImageFilter.GaussianBlur(feather))
    res = im.convert('RGBA'); res.putalpha(am)
    res.save(out)
    return dict(kept=round(float((alpha > 128).mean())*100, 2), bg=[int(v) for v in bg])
