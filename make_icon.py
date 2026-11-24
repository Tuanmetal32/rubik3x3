# Tạo biểu tượng PNG (rubik vàng-đen) không cần thư viện ngoài.
import zlib, struct, os, sys
def png(path, n):
    cols = [(255,193,7),(255,255,255),(211,47,47),(25,169,75),(30,91,216),(255,122,0),(255,214,0),(245,245,245),(255,193,7)]
    rows = []
    m = n*0.10; cell = (n-2*m)/3; gap = n*0.025; r = n*0.18
    for y in range(n):
        row = bytearray([0])
        for x in range(n):
            # nền bo góc màu đen
            dx = max(r-x, 0, x-(n-1-r)); dy = max(r-y, 0, y-(n-1-r))
            if dx*dx+dy*dy > r*r: row += bytes((0,0,0,0)); continue
            px = (20,18,14,255)
            cx = (x-m)/cell; cy = (y-m)/cell
            if 0 <= cx < 3 and 0 <= cy < 3:
                fx, fy = cx-int(cx), cy-int(cy)
                g = gap/cell
                if g < fx < 1-g and g < fy < 1-g:
                    px = cols[int(cy)*3+int(cx)] + (255,)
            row += bytes(px)
        rows.append(bytes(row))
    raw = b''.join(rows)
    def chunk(t, d): return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t+d) & 0xffffffff)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', n, n, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, 'wb').write(data)
for d, n in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    png(f'res/mipmap-{d}/ic_launcher.png', n)
