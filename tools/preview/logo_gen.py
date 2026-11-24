"""Genera el ícono (vector adaptativo + PNG) y el banner de Android TV.
Marca: un triángulo de "play" formado por barras de forma de onda."""
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter
REPO=os.path.abspath(os.path.join(os.path.dirname(__file__),"..",".."))
RES=REPO+"/app/src/main/res"
FD=RES+"/font/"
HEIGHTS=[1.0,0.84,0.68,0.52,0.36,0.2]   # play apuntando a la derecha
ORANGE1=(255,190,60); ORANGE2=(229,130,13)

def bars(cx,cy,size):
    """Rectángulos (x0,y0,x1,y1,r) de la marca centrada en cx,cy, alto total = size."""
    n=len(HEIGHTS); slot=size*0.92/n; w=slot*0.62; total=slot*n-(slot-w)
    x=cx-total/2+size*0.06; out=[]
    for h in HEIGHTS:
        bh=size*h; out.append((x,cy-bh/2,x+w,cy+bh/2,w/2)); x+=slot
    return out

def vector_path(rects):
    def f(v): return f"{v:.2f}"
    ps=[]
    for x0,y0,x1,y1,r in rects:
        ps.append(f"M{f(x0+r)},{f(y0)}H{f(x1-r)}A{f(r)},{f(r)} 0,0 1,{f(x1)},{f(y0+r)}V{f(y1-r)}A{f(r)},{f(r)} 0,0 1,{f(x1-r)},{f(y1)}H{f(x0+r)}A{f(r)},{f(r)} 0,0 1,{f(x0)},{f(y1-r)}V{f(y0+r)}A{f(r)},{f(r)} 0,0 1,{f(x0+r)},{f(y0)}Z")
    return "".join(ps)

def draw_mark(img,cx,cy,size):
    m=Image.new("L",img.size,0); d=ImageDraw.Draw(m)
    for x0,y0,x1,y1,r in bars(cx,cy,size): d.rounded_rectangle((x0,y0,x1,y1),radius=r,fill=255)
    grad=Image.new("RGB",img.size); gd=ImageDraw.Draw(grad)
    top=cy-size/2; 
    for y in range(img.size[1]):
        t=min(1,max(0,(y-top)/size)); gd.line([(0,y),(img.size[0],y)],fill=tuple(int(ORANGE1[i]*(1-t)+ORANGE2[i]*t) for i in range(3)))
    glow=m.filter(ImageFilter.GaussianBlur(size*0.08)).point(lambda v:int(v*0.45))
    img.paste((229,140,13),(0,0),glow)
    img.paste(grad,(0,0),m)

def bg(size,radius=None):
    im=Image.new("RGB",(size,size),(18,18,18)); d=ImageDraw.Draw(im)
    for y in range(size):
        t=y/size; d.line([(0,y),(size,y)],fill=(int(30-14*t),int(28-14*t),int(34-16*t)))
    return im

# ---- vector adaptativo (108dp, zona segura 66dp) ----
fg=vector_path(bars(54,54,40))
os.makedirs(RES+"/mipmap-anydpi-v26",exist_ok=True)
open(RES+"/drawable/ic_launcher_foreground.xml","w").write(f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="{fg}">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="54" android:startY="34" android:endX="54" android:endY="74">
                <item android:offset="0" android:color="#FFFFBE3C" />
                <item android:offset="1" android:color="#FFE5820D" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
''')
open(RES+"/drawable/ic_launcher_background.xml","w").write('''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="54" android:startY="0" android:endX="54" android:endY="108">
                <item android:offset="0" android:color="#FF1E1C22" />
                <item android:offset="1" android:color="#FF101012" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
''')
for name in ["ic_launcher","ic_launcher_round"]:
    open(RES+f"/mipmap-anydpi-v26/{name}.xml","w").write('''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
''')
# logo para usar dentro de la app (sin fondo)
lp=vector_path(bars(24,24,40))
open(RES+"/drawable/ic_logo.xml","w").write(f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="48dp" android:height="48dp" android:viewportWidth="48" android:viewportHeight="48">
    <path android:pathData="{lp}">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="24" android:startY="4" android:endX="24" android:endY="44">
                <item android:offset="0" android:color="#FFFFBE3C" />
                <item android:offset="1" android:color="#FFE5820D" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
''')
# ---- PNG para Android < 8 ----
for dens,px in {"mdpi":48,"hdpi":72,"xhdpi":96,"xxhdpi":144,"xxxhdpi":192}.items():
    S=px*4; im=bg(S); draw_mark(im,S/2,S/2,S*0.42)
    mask=Image.new("L",(S,S),0); ImageDraw.Draw(mask).rounded_rectangle((0,0,S-1,S-1),radius=S*0.22,fill=255)
    out=Image.new("RGBA",(S,S),(0,0,0,0)); out.paste(im,(0,0),mask)
    os.makedirs(RES+f"/mipmap-{dens}",exist_ok=True)
    out.resize((px,px),Image.LANCZOS).save(RES+f"/mipmap-{dens}/ic_launcher.png")
    out.resize((px,px),Image.LANCZOS).save(RES+f"/mipmap-{dens}/ic_launcher_round.png")
# ---- banner Android TV (320x180 en xhdpi) ----
def banner(w,h):
    S=4; W,H=w*S,h*S
    im=Image.new("RGB",(W,H)); d=ImageDraw.Draw(im)
    for x in range(W):
        t=x/W; d.line([(x,0),(x,H)],fill=(int(30-12*t),int(27-11*t),int(33-14*t)))
    ms=H*0.46; draw_mark(im,W*0.215,H/2,ms)
    d=ImageDraw.Draw(im)
    fb=ImageFont.truetype(FD+"dmsans_bold.ttf",int(H*0.235)); fr=ImageFont.truetype(FD+"dmsans_medium.ttf",int(H*0.105))
    x=W*0.39
    d.text((x,H*0.5),"Música",font=fb,fill=(255,255,255),anchor="ls")
    d.text((x+2,H*0.5+H*0.06),"PARA PLEX · TV",font=fr,fill=(229,160,13),anchor="lt")
    return im.resize((w,h),Image.LANCZOS)
for dens,(w,h) in {"xhdpi":(320,180),"xxhdpi":(480,270),"xxxhdpi":(640,360)}.items():
    os.makedirs(RES+f"/drawable-{dens}",exist_ok=True)
    banner(w,h).save(RES+f"/drawable-{dens}/banner.png")
# muestras para revisar
big=bg(512); draw_mark(big,256,256,215)
mask=Image.new("L",(512,512),0); ImageDraw.Draw(mask).rounded_rectangle((0,0,511,511),radius=112,fill=255)
o=Image.new("RGBA",(512,512),(0,0,0,0)); o.paste(big,(0,0),mask); o.save("icono.png")
banner(640,360).save("banner.png"); print("ok")
