// GENERADO por sprites/generar_frases.py desde sprites/frases.json. No editar a mano.
// Familias en el orden de la enum Familia de pantalla.cpp: generica, carnivora, girasol, cactus, suculenta, helecho (la primera es la comun).
#pragma once
#include <Arduino.h>

namespace frases {
constexpr int MAXIMO = 13;
constexpr int N_FAMILIAS = 6, N_ANIMOS = 11, N_EVENTOS = 9;
// Cada frase ocupa MAXIMO + 1 bytes (con el NUL). Se leen con memcpy_P.
const char ANIMO[N_FAMILIAS][N_ANIMOS][MAXIMO + 1] PROGMEM = {
    {  // generica
        "estoy bien",  // FELIZ
        "estoy cansada",  // CANSADA
        "tengo sed",  // SEDIENTA
        "mucha agua",  // AHOGADA
        "tengo frio",  // CON_FRIO
        "tengo calor",  // CON_CALOR
        "muy oscuro",  // A_OSCURAS
        "me deslumbra",  // DESLUMBRADA
        "me desmaye",  // DESMAYADA
        "zzz",  // DORMIDA
        "aun no se",  // SIN_DATOS
    },
    {  // carnivora
        "no molestar.",  // FELIZ
        "dejame en paz",  // CANSADA
        "AGUA. YA.",  // SEDIENTA
        "me ahogas!",  // AHOGADA
        "frio. odio.",  // CON_FRIO
        "arde. quita.",  // CON_CALOR
        "luz. ahora.",  // A_OSCURAS
        "apaga eso.",  // DESLUMBRADA
        "x_x (grr)",  // DESMAYADA
        "zzz. no tocar",  // DORMIDA
        "datos. ahora.",  // SIN_DATOS
    },
    {  // girasol
        "todo bello!",  // FELIZ
        "poquita pila",  // CANSADA
        "agua, porfa!",  // SEDIENTA
        "mucha agua ya",  // AHOGADA
        "brrr, frio",  // CON_FRIO
        "uf, que calor",  // CON_CALOR
        "y el sol?",  // A_OSCURAS
        "me encandilo",  // DESLUMBRADA
        "me fui... x_x",  // DESMAYADA
        "zzz... sol...",  // DORMIDA
        "y mis datos?",  // SIN_DATOS
    },
    {  // cactus
        "aqui, firme.",  // FELIZ
        "necesito paz",  // CANSADA
        "una gotica va",  // SEDIENTA
        "me ahogo!",  // AHOGADA
        "frio. raro.",  // CON_FRIO
        "esto si arde",  // CON_CALOR
        "oscuro aqui",  // A_OSCURAS
        "mucha luz, ya",  // DESLUMBRADA
        "x_x",  // DESMAYADA
        "zzz",  // DORMIDA
        "...",  // SIN_DATOS
    },
    {  // suculenta
        "todo en calma",  // FELIZ
        "despacito...",  // CANSADA
        "un sorbo, ya",  // SEDIENTA
        "basta de agua",  // AHOGADA
        "una cobijita",  // CON_FRIO
        "mucho sol, ya",  // CON_CALOR
        "algo de luz",  // A_OSCURAS
        "sombra, porfa",  // DESLUMBRADA
        "me desmaye...",  // DESMAYADA
        "zzz...",  // DORMIDA
        "esperando...",  // SIN_DATOS
    },
    {  // helecho
        "hoy si, bien",  // FELIZ
        "ya no puedo",  // CANSADA
        "me seco, agua",  // SEDIENTA
        "glu glu glu",  // AHOGADA
        "me congelo!",  // CON_FRIO
        "me derrito!",  // CON_CALOR
        "no veo nada!",  // A_OSCURAS
        "me quemo!",  // DESLUMBRADA
        "adios mundo",  // DESMAYADA
        "zzz (ronca)",  // DORMIDA
        "nadie me mide",  // SIN_DATOS
    },
};
const char EVENTO[N_FAMILIAS][N_EVENTOS][MAXIMO + 1] PROGMEM = {
    {  // generica
        "me regaron",  // REGADA
        "sali al sol",  // SALIO_AL_SOL
        "me desmaye",  // DESMAYO
        "revivi",  // REVIVIO
        "gracias",  // ACARICIADA
        "racha!",  // RACHA
        "",  // RIEGO_DECLARADO
        "si me regaron",  // RIEGO_CONFIRMADO
        "no lo note",  // RIEGO_DESMENTIDO
    },
    {  // carnivora
        "bien. sigue.",  // REGADA
        "sol. acepto.",  // SALIO_AL_SOL
        "x_x (grr)",  // DESMAYO
        "volvi. ojo.",  // REVIVIO
        "nam. casi.",  // ACARICIADA
        "racha. obvio.",  // RACHA
        "",  // RIEGO_DECLARADO
        "ya lo sabia.",  // RIEGO_CONFIRMADO
        "mentiroso.",  // RIEGO_DESMENTIDO
    },
    {  // girasol
        "gracias!",  // REGADA
        "solecito!",  // SALIO_AL_SOL
        "me fui... x_x",  // DESMAYO
        "volvi!",  // REVIVIO
        "jiji",  // ACARICIADA
        "racha! :D",  // RACHA
        "",  // RIEGO_DECLARADO
        "si me regaron",  // RIEGO_CONFIRMADO
        "no senti nada",  // RIEGO_DESMENTIDO
    },
    {  // cactus
        "ok. gracias.",  // REGADA
        "sol. bien.",  // SALIO_AL_SOL
        "x_x",  // DESMAYO
        "sigo aqui.",  // REVIVIO
        "ojo, pincho",  // ACARICIADA
        "racha.",  // RACHA
        "",  // RIEGO_DECLARADO
        "confirmado.",  // RIEGO_CONFIRMADO
        "no lo creo.",  // RIEGO_DESMENTIDO
    },
    {  // suculenta
        "que rico",  // REGADA
        "que calorcito",  // SALIO_AL_SOL
        "me desmaye...",  // DESMAYO
        "ya volvi",  // REVIVIO
        "mmm, gracias",  // ACARICIADA
        "otro dia bien",  // RACHA
        "",  // RIEGO_DECLARADO
        "si, lo senti",  // RIEGO_CONFIRMADO
        "no llego nada",  // RIEGO_DESMENTIDO
    },
    {  // helecho
        "al fin! agua!",  // REGADA
        "luz! luz!",  // SALIO_AL_SOL
        "adios mundo",  // DESMAYO
        "revivi!!",  // REVIVIO
        "mas, mas!",  // ACARICIADA
        "soy un heroe",  // RACHA
        "",  // RIEGO_DECLARADO
        "si me regaron",  // RIEGO_CONFIRMADO
        "mentira!",  // RIEGO_DESMENTIDO
    },
};
}
