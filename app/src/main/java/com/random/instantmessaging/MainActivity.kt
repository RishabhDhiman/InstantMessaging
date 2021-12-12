package com.random.instantmessaging

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.PeerConnection.IceServer
import java.nio.ByteBuffer
import java.nio.charset.Charset


class MainActivity : AppCompatActivity() {
    private var iceCandidateSetted = false
    private var output: TextView? = null
    private var client: OkHttpClient? = null
    val listener = EchoWebSocketListener()
    val pcObserver = PeerConnectionObserver()
    val sdpMediaConstraints = MediaConstraints()
    lateinit var peerConnection: PeerConnection
    lateinit var peerConnectionFactory: PeerConnectionFactory
    val sdpObserver = SDPObserverImplementation()
    lateinit var webSocket: WebSocket
    val sdp = JSONObject()
    var i = 0
    lateinit var rootEglBase: EglBase
    val iceCandidate = arrayListOf<IceCandidate>()
    var localSessionDescription: SessionDescription? = null
    var isInitiator = true
    var dataChannel: DataChannel? = null
    var remoteSessionDescription: SessionDescription? = null
    var attemptOffer = true
    var attemptAnswer = true
    var isRemoteSessionDescriptionSetted = false
    var list = ArrayList<String>()
    lateinit var arrayAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        arrayAdapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, list)
        messageList.adapter = arrayAdapter
        rootEglBase = EglBase.create()
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this)
                .createInitializationOptions()
        )
        connect.setOnClickListener {
            start()
            connect.visibility = View.GONE
        }
        send.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                dataChannel!!.send(stringToByteBuffer(message.text.toString()))
            }
        }) Socket
                setupPeerConnectionFactory()
    }


    fun stringToByteBuffer(text: String): DataChannel.Buffer {
        val byteBuffer = ByteBuffer.wrap(text.toByteArray())
        val buffer = DataChannel.Buffer(byteBuffer, false)
        return buffer
    }

    private fun start() {
        client = OkHttpClient()
        val request: Request =
            Request.Builder().url("ws://socketsignalingserver.herokuapp.com").build()
        //Request.Builder().url("ws://192.168.43.84:9090").build()
        val ws = client!!.newWebSocket(request, listener)
        client!!.dispatcher().executorService().shutdown()
    }


    fun setupPeerConnectionFactory() {
        val options = PeerConnectionFactory.Options()

        val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(
            rootEglBase.eglBaseContext,
            true,
            true
        )
        val defaultVideoDecoderFactory =
            DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(defaultVideoEncoderFactory)
            .setVideoDecoderFactory(defaultVideoDecoderFactory)
            .setOptions(options)
            .createPeerConnectionFactory()
        val list = arrayListOf<IceServer>()
        list.add(IceServer("stun:stun1.l.google.com:19302"))
        list.add(IceServer("stun:stun2.l.google.com:19302"))
        list.add(IceServer("stun:stun.ucallweconn.net:3478"))
        list.add(
            IceServer(
                "turn:numb.viagenie.ca",
                "webrtc@live.com",
                "muazkh"
            )
        )
        list.add(
            IceServer(
                "turn:numb.viagenie.ca",
                "kobisa1025@temhuv.com",
                "2eAFfy29q@6EpYp"
            )
        )
        list.add(IceServer("turn:turn.anyfirewall.com:443?transport=tcp", "webrtc", "webrtc"))
        peerConnection = peerConnectionFactory.createPeerConnection(list, pcObserver)!!
        val init = DataChannel.Init()
        init.id = 1
        if (isInitiator) {
            dataChannel = peerConnection.createDataChannel("sendmessage", init)
        }

        sdpMediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "OfferToReceiveAudio",
                "true"
            )
        )
        sdpMediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "OfferToReceiveVideo",
                "true"
            )
        )

    }


    private fun output(txt: String) {
        try {
            Log.e("Json", txt)
            val jsonObject = JSONObject(txt)
            if (jsonObject.getString("type").equals("candidate")) {
                val iceCandidate = jsonObject.getString("iceCandidate")
                Log.e("Flow", "Candidate Arrived")
                peerConnection.addIceCandidate(
                    Gson().fromJson(
                        iceCandidate,
                        IceCandidate::class.java
                    )
                )
            } else if (jsonObject.getString("type").equals("initiator")) {
                isInitiator = jsonObject.getBoolean("initiator")
                Log.e("Flow", "Is Initiator" + isInitiator.toString())
            } else if (jsonObject.getString("type").equals("peerArrived")) {
                peerConnection.createOffer(sdpObserver, sdpMediaConstraints)
            } else if (jsonObject.getString("type").equals("offer")) {
                remoteSessionDescription = Gson().fromJson(
                    jsonObject.getString("sessionDescription"),
                    SessionDescription::class.java
                )
                peerConnection.setRemoteDescription(sdpObserver, remoteSessionDescription)
                Log.e("Flow", "Offer Arrived")
                Log.e("Flow", "Remote Description Setted")
            } else if (jsonObject.getString("type").equals("answer")) {
                peerConnection.setRemoteDescription(
                    sdpObserver,
                    Gson().fromJson(
                        jsonObject.getString("sessionDescription"),
                        SessionDescription::class.java
                    )
                )
                Log.e("Flow", "Answer Arrived")
                Log.e("Flow", "Remote Description Setted")
            } else if (jsonObject.getString("type").equals("leave")) {
                attemptOffer = true
                attemptAnswer = true
                isRemoteSessionDescriptionSetted = false
                start()
                setupPeerConnectionFactory()
                runOnUiThread {
                    status.text = "Offline"
                    status.setTextColor(resources.getColor(R.color.red))
                }
            } else if (jsonObject.getString("type").equals("roomFull")) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Room is full", Toast.LENGTH_LONG).show()
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }


    private fun stringToByteBuffer(
        msg: String,
        charset: Charset
    ): ByteBuffer? {
        return ByteBuffer.wrap(msg.toByteArray(charset))
    }

    inner class SDPObserverImplementation : SdpObserver {
        override fun onSetFailure(p0: String?) {
        }

        override fun onSetSuccess() {
            if (isInitiator && attemptOffer) {
                /**
                 *
                 * Success after setting local description in peer connection author
                 */
                listener.attemptOffer()
                attemptOffer = false
            } else if (!isInitiator && attemptAnswer) {
                /**
                 *
                 * Success after setting remote description in peer connection follower
                 */
                peerConnection.createAnswer(sdpObserver, sdpMediaConstraints)
                isRemoteSessionDescriptionSetted = true
            } else if (!attemptAnswer) {
                /**
                 *
                 * Success after setting local description in peer connection follower
                 */
                listener.attemptAnswer()
            } else {
                /**
                 *
                 * Success after setting remote description in peer connection author
                 */
                isRemoteSessionDescriptionSetted = true
            }
        }

        override fun onCreateSuccess(p0: SessionDescription?) {
            localSessionDescription = p0
            if (isInitiator) {
                peerConnection.setLocalDescription(sdpObserver, p0)
                Log.e("Flow", "Local Description Setted")
            } else {
                peerConnection.setLocalDescription(sdpObserver, localSessionDescription)
                Log.e("Flow", "Local Description Setted")
                attemptAnswer = false
            }
        }

        override fun onCreateFailure(p0: String?) {
            Log.e("Failure", p0)
        }
    }

    inner class PeerConnectionObserver : PeerConnection.Observer {
        override fun onIceCandidate(p0: IceCandidate?) {
            val jsonObject = JSONObject()
            jsonObject.put("roomName", roomId.text.toString())
            jsonObject.put("type", "candidate")
            jsonObject.put("isInitiator", isInitiator)
            jsonObject.put("iceCandidate", Gson().toJson(p0))
            webSocket.send(jsonObject.toString())
        }

        override fun onDataChannel(p0: DataChannel?) {
            p0!!.registerObserver(object : DataChannel.Observer {
                override fun onMessage(p0: DataChannel.Buffer?) {
                    Log.e("DatChannel", "Datachannel")
                    val data: ByteBuffer = p0!!.data
                    val bytes = ByteArray(data.capacity())
                    data[bytes]
                    val strData = String(bytes)
                    runOnUiThread {
                        list.add(strData)
                        arrayAdapter.notifyDataSetChanged()
                    }
                }

                override fun onBufferedAmountChange(p0: Long) {
                    Log.e("DatChannel", "Datachannel")
                }

                override fun onStateChange() {
                    Log.e("DatChannel", "Datachannel")
                    Log.e("DatChannel", "Datachannel")
                }

            })
        }

        override fun onIceConnectionReceivingChange(p0: Boolean) {
            Log.e("Error", "onIceConnectionReceivingChange")
        }

        override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
            if (PeerConnection.IceConnectionState.CONNECTED == p0) {
                runOnUiThread {
                    status.text = "Online"
                    status.setTextColor(resources.getColor(R.color.green))
                }
            }
        }

        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
            if (p0 == PeerConnection.IceGatheringState.COMPLETE) {
                Log.e("IceCandidate", "Gathered")
                Log.e("Generated Candidate", JSONArray(Gson().toJson(iceCandidate)).toString())
            }
        }

        override fun onAddStream(p0: MediaStream?) {
            Log.e("Error", "Add Stream")
        }

        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
            Log.e("Error", "On Signalling Change")
        }

        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {

        }

        override fun onRemoveStream(p0: MediaStream?) {
            Log.e("Error", "ON Remove Stream")
        }

        override fun onRenegotiationNeeded() {
            Log.e("Error", "ON Renogotiation Needed")
        }

        override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
            Log.e("Error", "ONAddTrack")
        }
    }


    inner class EchoWebSocketListener : WebSocketListener() {

        override fun onOpen(mWebSocket: WebSocket, response: Response?) {
            webSocket = mWebSocket
            login()
        }

        fun login() {
            val jsonObject = JSONObject()
            jsonObject.put("name", name.text.toString())
            jsonObject.put("type", "login")
            jsonObject.put("roomName", roomId.text.toString())
            webSocket.send(jsonObject.toString())
        }

        fun attemptOffer() {
            val jsonObject = JSONObject()
            jsonObject.put("name", name.text.toString())
            jsonObject.put("type", "offer")
            jsonObject.put("roomName", roomId.text.toString())
            jsonObject.put("sessionDescription", Gson().toJson(peerConnection.localDescription))
            webSocket.send(jsonObject.toString())
        }

        fun attemptAnswer() {
            val jsonObject = JSONObject()
            jsonObject.put("name", name.text.toString())
            jsonObject.put("type", "answer")
            jsonObject.put("roomName", roomId.text.toString())
            jsonObject.put("sessionDescription", Gson().toJson(peerConnection.localDescription))
            webSocket.send(jsonObject.toString())
        }

        fun sendIceCandidates() {
            val jsonObject = JSONObject()
            jsonObject.put(
                "iceCandidate",
                JSONArray(Gson().toJson(iceCandidate))
            )
            webSocket.send(jsonObject.toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            output(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            output(bytes.hex())
        }

        override fun onClosing(
            webSocket: WebSocket,
            code: Int,
            reason: String
        ) {
            webSocket.close(
                1000,
                null
            )
        }

        override fun onFailure(
            webSocket: WebSocket?,
            t: Throwable,
            response: Response?
        ) {
            output("Error : " + t.message)
        }
    }

}
