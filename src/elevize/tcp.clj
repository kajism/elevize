(ns elevize.tcp
  (:require [clojure.java.io :as io]
            [taoensso.timbre :as timbre])
  (:import java.io.StringWriter
           [java.net DatagramPacket DatagramSocket InetAddress InetSocketAddress MulticastSocket Socket SocketAddress]))

(def connect-timeout (* 5 1000))
(def read-timeout (* 5 1000))

(defn send-request
  "Sends data to the specified host and port and reads the response"
  [host port data]
  (with-open [sock (doto (Socket.) ;; host port
                     (.setSoTimeout read-timeout))]
    (let [_ (.connect sock (InetSocketAddress. (InetAddress/getByName host) port) connect-timeout)
          writer (io/writer sock)
          reader (io/reader sock)
          response (StringWriter.)]
      (.append writer data)
      (.flush writer)
      (io/copy reader response)
      (str response))))

;;--- UDP Multicasting
(def buffer-size 51200)

(defn receive-datagram
  "Block until a UDP message is received on the given DatagramSocket, and
  return the payload message as a string."
  [^DatagramSocket socket]
  (let [buffer (byte-array buffer-size)
        packet (DatagramPacket. buffer buffer-size)]
    (.receive socket packet)
    (String. (.getData packet) 0 (.getLength packet))))

(defn multicast-socket [group-addr port]
  (let [group (InetAddress/getByName group-addr)
        socket (doto (MulticastSocket. port)
                 (.setSoTimeout read-timeout))]
    (.joinGroup socket group)
    socket))

(defn multicast-leave-group [^MulticastSocket socket group-addr]
  (try
    (.leaveGroup socket (InetAddress/getByName group-addr))
    (catch Throwable e
      (timbre/info {:a ::leaving-multicast-group-failed :ex-msg (.getMessage e) :ex-type (type e)}))))
