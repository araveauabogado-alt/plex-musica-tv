# Previews de diseño

Dibujan las pantallas con PIL usando la fuente real de la app (DM Sans en `res/font`) e íconos de `res/drawable` (vía `rsvg-convert`). Las carátulas son de relleno.

    python3 tools/preview/preview_gen.py   # 1_principal, 2_reproduciendo, 3_cola
    python3 tools/preview/preview_home.py  # 4_inicio
    python3 tools/preview/preview_np.py    # 5_reproduciendo_v2 (fondo de dos colores, onda, zona de avance)

Las imágenes quedan en la carpeta desde donde se ejecuta.
