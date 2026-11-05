package com.vivero.fitodiagnostico.dominio.servicio;

/**
 * Regla de descubrimiento: ¿dos direcciones están en la misma red local? Sí si son la misma dirección, o si las
 * dos son IPv4 privadas (10/8, 172.16/12, 192.168/16) y comparten los primeros 24 bits. Función pura, solo JDK.
 *
 * <p>Es una aproximación a propósito: el servidor no conoce la máscara real de la red del usuario. Una /24 es la
 * máscara de casi todas las redes domésticas y de los hotspots de celular. Cualquier cosa que no se pueda leer
 * como IPv4 distinta a la otra da {@code false}: ante la duda no se muestra la matera.
 */
public final class MismaRed {

    private MismaRed() { }

    public static boolean entre(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return false;
        }
        String ia = a.trim();
        String ib = b.trim();
        if (ia.equalsIgnoreCase(ib)) {
            return true;
        }
        int[] oa = octetos(ia);
        int[] ob = octetos(ib);
        if (oa == null || ob == null || !esPrivada(oa) || !esPrivada(ob)) {
            return false;
        }
        return oa[0] == ob[0] && oa[1] == ob[1] && oa[2] == ob[2];
    }

    private static boolean esPrivada(int[] o) {
        return o[0] == 10
                || (o[0] == 172 && o[1] >= 16 && o[1] <= 31)
                || (o[0] == 192 && o[1] == 168);
    }

    /** Los cuatro octetos de una IPv4 en notación decimal estricta, o null si no lo es. */
    private static int[] octetos(String ip) {
        String[] partes = ip.split("\\.", -1);
        if (partes.length != 4) {
            return null;
        }
        int[] r = new int[4];
        for (int i = 0; i < 4; i++) {
            String p = partes[i];
            if (p.isEmpty() || p.length() > 3) {
                return null;
            }
            for (int k = 0; k < p.length(); k++) {
                if (p.charAt(k) < '0' || p.charAt(k) > '9') {
                    return null;
                }
            }
            r[i] = Integer.parseInt(p);
            if (r[i] > 255) {
                return null;
            }
        }
        return r;
    }
}
