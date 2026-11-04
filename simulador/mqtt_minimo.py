"""Cliente MQTT 3.1.1 minimo sobre `socket` (solo biblioteca estandar), lo que haria el firmware con una libreria de C.

Soporta lo justo para la matera: CONNECT/CONNACK (clientId, cleanSession, keepalive), PUBLISH QoS 1 con PUBACK y
reintento (DUP), SUBSCRIBE/SUBACK, PINGREQ/PINGRESP por keepalive, DISCONNECT y lectura de PUBLISH entrantes (QoS 0 y 1;
a los QoS 1 les devuelve PUBACK). No hace QoS 2, ni TLS, ni will, ni reconexion automatica: si algo falla lanza
ErrorMqtt (que es OSError) y el que llama decide (el simulador lo trata como "sin red" y encola).
"""

import socket
import time

CONNECT, CONNACK, PUBLISH, PUBACK, SUBSCRIBE, SUBACK, PINGREQ, PINGRESP, DISCONNECT = 1, 2, 3, 4, 8, 9, 12, 13, 14


class ErrorMqtt(OSError):
    """Fallo de conexion o de protocolo con el broker."""


def _cadena(texto):
    datos = texto.encode("utf-8")
    return len(datos).to_bytes(2, "big") + datos


def _longitud(n):
    """Remaining Length: 7 bits por byte, el bit alto indica que sigue otro."""
    salida = bytearray()
    while True:
        byte, n = n % 128, n // 128
        salida.append(byte | (0x80 if n else 0))
        if not n:
            return bytes(salida)


def _paquete(tipo, banderas, cuerpo):
    return bytes([(tipo << 4) | banderas]) + _longitud(len(cuerpo)) + cuerpo


class ClienteMqtt:
    def __init__(self, host, puerto, client_id, keepalive=30, clean_session=False, timeout=5.0):
        self.host, self.puerto, self.client_id = host, puerto, client_id
        self.keepalive, self.clean_session, self.timeout = keepalive, clean_session, timeout
        self.sock = None
        self.entrantes = []          # [(topico, payload)] ya reconocidos, a la espera de que alguien los lea
        self.sesion_previa = False   # CONNACK "session present"
        self._buf = bytearray()
        self._id = 0
        self._ultimo_envio = 0.0
        self._ping_pendiente = False

    # ---- conexion ----

    def conectar(self):
        self.cerrar()
        try:
            self.sock = socket.create_connection((self.host, self.puerto), timeout=self.timeout)
        except OSError as error:
            self.sock = None
            raise ErrorMqtt("no hay broker en %s:%s (%s)" % (self.host, self.puerto, error))
        self._buf.clear()
        self._ping_pendiente = False
        banderas = 0x02 if self.clean_session else 0x00
        variable = _cadena("MQTT") + bytes([4, banderas]) + self.keepalive.to_bytes(2, "big")
        self._enviar(_paquete(CONNECT, 0, variable + _cadena(self.client_id)))
        tipo, _, cuerpo = self._esperar_paquete(lambda t: t == CONNACK, self.timeout)
        if len(cuerpo) < 2 or cuerpo[1] != 0:
            self.cerrar()
            raise ErrorMqtt("el broker rechazo la conexion (codigo %s)" % (cuerpo[1] if len(cuerpo) > 1 else "?"))
        self.sesion_previa = bool(cuerpo[0] & 1)

    def conectado(self):
        return self.sock is not None

    def cerrar(self):
        if self.sock is not None:
            try:
                self.sock.close()
            except OSError:
                pass
        self.sock = None

    def desconectar(self):
        if self.sock is not None:
            try:
                self._enviar(_paquete(DISCONNECT, 0, b""))
            except OSError:
                pass
        self.cerrar()

    # ---- operaciones ----

    def suscribir(self, topico, qos=1):
        pid = self._nuevo_id()
        self._enviar(_paquete(SUBSCRIBE, 2, pid.to_bytes(2, "big") + _cadena(topico) + bytes([qos])))
        _, _, cuerpo = self._esperar_paquete(lambda t, c=None: t == SUBACK, self.timeout)
        if len(cuerpo) < 3 or cuerpo[2] == 0x80:
            raise ErrorMqtt("el broker rechazo la suscripcion a " + topico)

    def publicar(self, topico, payload, qos=1, reintentos=2, espera=5.0):
        """QoS 1: reenvia con DUP hasta `reintentos` veces si no llega el PUBACK; despues lanza ErrorMqtt."""
        if isinstance(payload, str):
            payload = payload.encode("utf-8")
        pid = self._nuevo_id()
        for intento in range(reintentos + 1):
            banderas = (qos << 1) | (0x08 if intento else 0)
            self._enviar(_paquete(PUBLISH, banderas, _cadena(topico) + pid.to_bytes(2, "big") + payload))
            if qos == 0:
                return
            try:
                self._esperar_paquete(lambda t, c=None, p=pid: False, espera, hasta_puback=pid)
                return
            except TimeoutError:
                continue
        raise ErrorMqtt("el broker no confirmo (PUBACK) la publicacion en " + topico)

    def esperar(self, segundos):
        """Atiende el socket `segundos` (PUBLISH entrantes, PINGRESP) y manda PINGREQ si hace falta mantener viva la
        conexion. Devuelve lo recibido en este rato: [(topico, payload)]. Lanza ErrorMqtt si el broker se cae."""
        limite = time.monotonic() + segundos
        while True:
            self._mantener_viva()
            restante = limite - time.monotonic()
            if restante <= 0:
                break
            try:
                self._leer_uno(min(restante, 1.0))
            except TimeoutError:
                pass
        recibidos, self.entrantes = self.entrantes, []
        return recibidos

    def tomar_entrante(self, espera):
        """Espera hasta `espera` s un PUBLISH entrante y lo devuelve (topico, payload), o None."""
        limite = time.monotonic() + espera
        while not self.entrantes:
            self._mantener_viva()
            restante = limite - time.monotonic()
            if restante <= 0:
                return None
            try:
                self._leer_uno(min(restante, 1.0))
            except TimeoutError:
                pass
        return self.entrantes.pop(0)

    # ---- internos ----

    def _nuevo_id(self):
        self._id = self._id % 65535 + 1
        return self._id

    def _enviar(self, datos):
        if self.sock is None:
            raise ErrorMqtt("sin conexion con el broker")
        try:
            self.sock.sendall(datos)
        except OSError as error:
            self.cerrar()
            raise ErrorMqtt("se perdio la conexion con el broker: %s" % error)
        self._ultimo_envio = time.monotonic()

    def _mantener_viva(self):
        if self.sock is not None and time.monotonic() - self._ultimo_envio >= self.keepalive / 2:
            self._enviar(_paquete(PINGREQ, 0, b""))
            self._ping_pendiente = True

    def _recibir(self, hasta):
        """Lee del socket hasta `hasta` (monotonic) y agrega al buffer; TimeoutError si no llega nada."""
        restante = hasta - time.monotonic()
        if restante <= 0:
            raise TimeoutError()
        self.sock.settimeout(restante)
        try:
            datos = self.sock.recv(4096)
        except (socket.timeout, TimeoutError):
            raise TimeoutError()
        except OSError as error:
            self.cerrar()
            raise ErrorMqtt("se perdio la conexion con el broker: %s" % error)
        if not datos:
            self.cerrar()
            raise ErrorMqtt("el broker cerro la conexion")
        self._buf.extend(datos)

    def _sacar_paquete(self):
        """(tipo, banderas, cuerpo) si el buffer ya trae un paquete completo; si no, None."""
        if len(self._buf) < 2:
            return None
        multiplicador, longitud, i = 1, 0, 1
        while True:
            if i >= len(self._buf):
                return None
            byte = self._buf[i]
            longitud += (byte & 0x7F) * multiplicador
            i += 1
            if not byte & 0x80:
                break
            multiplicador *= 128
            if multiplicador > 128 ** 3:
                self.cerrar()
                raise ErrorMqtt("paquete mal formado del broker")
        if len(self._buf) < i + longitud:
            return None
        primero = self._buf[0]
        cuerpo = bytes(self._buf[i:i + longitud])
        del self._buf[:i + longitud]
        return primero >> 4, primero & 0x0F, cuerpo

    def _leer_uno(self, espera, hasta_puback=None):
        """Procesa un paquete: guarda/reconoce PUBLISH, anota PINGRESP. Devuelve (tipo, banderas, cuerpo)."""
        limite = time.monotonic() + espera
        while True:
            paquete = self._sacar_paquete()
            if paquete is not None:
                break
            self._recibir(limite)
        tipo, banderas, cuerpo = paquete
        if tipo == PUBLISH:
            largo = int.from_bytes(cuerpo[:2], "big")
            topico = cuerpo[2:2 + largo].decode("utf-8", errors="replace")
            resto = cuerpo[2 + largo:]
            qos = (banderas >> 1) & 3
            if qos:
                self._enviar(_paquete(PUBACK, 0, resto[:2]))
                resto = resto[2:]
            self.entrantes.append((topico, resto))
        elif tipo == PINGRESP:
            self._ping_pendiente = False
        return paquete

    def _esperar_paquete(self, quiero, espera, hasta_puback=None):
        """Lee hasta un paquete que `quiero(tipo)` acepte (o el PUBACK `hasta_puback`); TimeoutError si no llega."""
        limite = time.monotonic() + espera
        while True:
            paquete = self._leer_uno(max(limite - time.monotonic(), 0.001))
            tipo, _, cuerpo = paquete
            if hasta_puback is not None and tipo == PUBACK and int.from_bytes(cuerpo[:2], "big") == hasta_puback:
                return paquete
            if hasta_puback is None and quiero(tipo):
                return paquete
