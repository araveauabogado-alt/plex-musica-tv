import os
REPO=os.path.abspath(os.path.join(os.path.dirname(__file__),"..",".."))
HERE=os.path.dirname(os.path.abspath(__file__))
src=open(os.path.join(HERE,"preview_gen.py")).read()
exec(src[src.index("import math"):src.index("# ---------- 1.")])
im=Image.new("RGBA",(W,H),BG+(255,)); ov=Image.new("RGBA",(W,H),(0,0,0,0)); dr=ImageDraw.Draw(ov)
bx0=d(48); by=d(27); bx1=W-d(48); bh=d(64)
dr.rounded_rectangle((bx0,by,bx1,by+bh),radius=d(10),fill=(38,38,42,255))
im=Image.alpha_composite(im,ov); ov=Image.new("RGBA",(W,H),(0,0,0,0)); dr=ImageDraw.Draw(ov)
cvb,_,_=cover(bh,77); mk=Image.new("L",(bh,bh),0); ImageDraw.Draw(mk).rounded_rectangle((0,0,bh*2,bh-1),radius=d(10),fill=255)
im.paste(cvb,(bx0,by),mk)
dr.text((bx0+bh+d(16),by+d(14)),"Ella Me Levantó ft. Boogie Mike (Bruno Borlone Bootleg)",font=F("m",17),fill=(255,255,255,255))
dr.text((bx0+bh+d(16),by+d(38)),"Plex Música TV · Bruno Borlone · Remixes Collection",font=F("r",13),fill=(170,170,170,255))
fb=F("m",13)
def small_btn(xr,text,foc):
    w=dr.textlength(text,font=fb)+d(32); dr.rounded_rectangle((xr-w,by+d(15),xr,by+d(49)),radius=d(17),fill=(229,160,13,255) if foc else (255,255,255,38))
    dr.text((xr-w+d(16),by+d(32)),text,font=fb,fill=(0,0,0,255) if foc else (255,255,255,255),anchor="lm"); return xr-w-d(8)
xr=small_btn(bx1-d(12),"Pausar",False); small_btn(xr,"Abrir",True)
x=d(54); y=d(27+64+12)
for t,f,s in [("Inicio",False,True),("▶ Reproduciendo",0,0),("Artistas",0,0),("Álbumes",0,0),("Playlists",0,0),("Buscar",0,0),("Orden: A–Z",0,0)]:
    x=pill(dr,x,y,t,f,s)
im=Image.alpha_composite(im,ov); dr=ImageDraw.Draw(im)
shelves=[("Lanzamientos recientes",[("Wall of Eyes","The Smile · 26 ene 2024"),("Romance","Fontaines D.C. · 23 ago 2024"),("Bright Future","Adrianne Lenker · 22 mar 2024"),("Tigers Blood","Waxahatchee · 22 mar 2024"),("Hit Me Hard and Soft","Billie Eilish · 17 may 2024"),("Las Dos Puertas","Mon Laferte · 2024"),("Diamond Jubilee","Cindy Lee · 29 mar 2024")]),
 ("Reproducciones recientes",[("Mezzanine","Massive Attack"),("Kind of Blue","Miles Davis"),("In Rainbows","Radiohead"),("Etazhi","Molchat Doma"),("Big Calm","Morcheeba"),("Ethio Jazz","Mulatu Astatke"),("Play","Moby")]),
 ("Agregados recientemente",[("Blue Lines","Massive Attack"),("Moon Safari","Air"),("Dummy","Portishead"),("Maxinquaye","Tricky"),("Protection","Massive Attack"),("Endtroducing.....","DJ Shadow"),("Selected Ambient Works","Aphex Twin")]),
 ("Lanzados un día como hoy",[("Third","Portishead · Hace 25 años"),("Ágata","Ejemplo · Hace 10 años")])]
y=d(92+76); cw=d(124)
for si,(title,items) in enumerate(shelves):
    dr.text((d(54),y),title,font=F("b",18),fill=WHITE); y+=d(30)
    for i,(t,s) in enumerate(items):
        x=d(48)+i*cw; foc=False
        pad=d(6); inner=cw-2*d(6)-2*pad
        card=Image.new("RGBA",(cw-2*d(6),d(148)),(0,0,0,0)); cd=ImageDraw.Draw(card)
        if foc: cd.rounded_rectangle((0,0,card.width-1,card.height-1),radius=d(10),fill=(42,42,42),outline=ACC,width=d(3))
        cv,_,_=cover(int(inner),si*10+i+3); card.paste(cv,(pad,pad))
        cd.text((pad,pad+inner+d(5)),t if len(t)<17 else t[:15]+"…",font=F("m",13),fill=WHITE)
        cd.text((pad,pad+inner+d(23)),s if len(s)<19 else s[:17]+"…",font=F("r",11),fill=SUB)
        if foc: card=card.resize((int(card.width*1.1),int(card.height*1.1)),Image.LANCZOS)
        im.alpha_composite(card,(int(x+d(6)-(card.width-(cw-2*d(6)))/2),int(y-(card.height-d(148))/2)))
    y+=d(160)
im.convert("RGB").save("4_inicio.png"); print("ok")
