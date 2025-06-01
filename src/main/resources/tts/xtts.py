from TTS.tts.configs.xtts_config import XttsConfig
from TTS.tts.models.xtts import Xtts
import sys
import signal
from socket import socket, AF_INET, SOCK_STREAM
import json

comm_socket = socket(AF_INET, SOCK_STREAM)
comm_socket.connect(("localhost", int(sys.argv[1])))

config = XttsConfig()
config.audio = None  # Set audio to None to avoid loading audio config
config.load_json(f"{sys.argv[3]}/config.json")
model = Xtts.init_from_config(config)
model.load_checkpoint(config, checkpoint_dir=sys.argv[3], eval=True)
model.cuda()

(gpt_cond_latent, speaker_embedding) = model.get_conditioning_latents(
    audio_path=sys.argv[2]
)

def read_next_msg():
    msg = bytearray()
    while True:
        content = comm_socket.recv(1)
        if len(content) < 1:
            exit(0)

        bt = content[0]
        if bt == 1:
            break
        msg.append(bt)

    return json.loads(msg.decode('utf-8'))

def signal_handler(sig, frame):
    comm_socket.shutdown()
    comm_socket.close()

signal.signal(signal.SIGINT, signal_handler)

while True:
    next_msg = read_next_msg()

    for index, chunk in enumerate(model.inference_stream(
            next_msg['text'],
            next_msg['language'],
            gpt_cond_latent,
            speaker_embedding,
            stream_chunk_size=40,
            enable_text_splitting=True
    )):
        data = bytes(chunk.cpu().numpy())
        comm_socket.send(len(data).to_bytes(4, byteorder='big'))
        comm_socket.send(data)
    comm_socket.send(bytearray([0, 0, 0, 0]))
