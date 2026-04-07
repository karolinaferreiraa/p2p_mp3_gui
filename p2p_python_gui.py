import os
import socket
import threading
import tkinter as tk
from tkinter import ttk, messagebox, simpledialog

# ==========================================
# CONFIGURAÇÕES
# ==========================================
PASTA_COMPARTILHADOS = "compartilhados"
PASTA_RECEBIDOS = "recebidos"
BUFFER = 4096

os.makedirs(PASTA_COMPARTILHADOS, exist_ok=True)
os.makedirs(PASTA_RECEBIDOS, exist_ok=True)


class P2PGUI:
    def __init__(self, root):
        self.root = root
        self.root.title("P2P MP3 - Python GUI")
        self.root.geometry("760x560")

        self.server_socket = None
        self.client_socket = None
        self.connected = False

        self.build_ui()

    # ------------------------------------------
    # MONTAGEM DA INTERFACE
    # ------------------------------------------
    def build_ui(self):
        frm_top = ttk.LabelFrame(self.root, text="Servidor")
        frm_top.pack(fill="x", padx=10, pady=8)

        ttk.Label(frm_top, text="Host:").grid(row=0, column=0, padx=5, pady=5, sticky="w")
        self.ent_host_server = ttk.Entry(frm_top)
        self.ent_host_server.insert(0, "0.0.0.0")
        self.ent_host_server.grid(row=0, column=1, padx=5, pady=5)

        ttk.Label(frm_top, text="Porta:").grid(row=0, column=2, padx=5, pady=5, sticky="w")
        self.ent_port_server = ttk.Entry(frm_top)
        self.ent_port_server.insert(0, "5000")
        self.ent_port_server.grid(row=0, column=3, padx=5, pady=5)

        ttk.Button(frm_top, text="Iniciar Servidor", command=self.start_server).grid(row=0, column=4, padx=5, pady=5)

        frm_client = ttk.LabelFrame(self.root, text="Cliente")
        frm_client.pack(fill="x", padx=10, pady=8)

        ttk.Label(frm_client, text="IP Remoto:").grid(row=0, column=0, padx=5, pady=5, sticky="w")
        self.ent_host_client = ttk.Entry(frm_client)
        self.ent_host_client.insert(0, "127.0.0.1")
        self.ent_host_client.grid(row=0, column=1, padx=5, pady=5)

        ttk.Label(frm_client, text="Porta:").grid(row=0, column=2, padx=5, pady=5, sticky="w")
        self.ent_port_client = ttk.Entry(frm_client)
        self.ent_port_client.insert(0, "5000")
        self.ent_port_client.grid(row=0, column=3, padx=5, pady=5)

        ttk.Button(frm_client, text="Conectar", command=self.connect_peer).grid(row=0, column=4, padx=5, pady=5)
        ttk.Button(frm_client, text="Listar MP3", command=self.list_remote_files).grid(row=0, column=5, padx=5, pady=5)
        ttk.Button(frm_client, text="Baixar Selecionado", command=self.download_selected).grid(row=0, column=6, padx=5, pady=5)

        frm_list = ttk.LabelFrame(self.root, text="Arquivos Remotos")
        frm_list.pack(fill="both", expand=False, padx=10, pady=8)

        self.listbox = tk.Listbox(frm_list, height=10)
        self.listbox.pack(fill="both", expand=True, padx=8, pady=8)

        frm_progress = ttk.LabelFrame(self.root, text="Progresso do Download")
        frm_progress.pack(fill="x", padx=10, pady=8)

        self.progress = ttk.Progressbar(frm_progress, orient="horizontal", mode="determinate", maximum=100)
        self.progress.pack(fill="x", padx=8, pady=8)

        self.lbl_progress = ttk.Label(frm_progress, text="Aguardando...")
        self.lbl_progress.pack(anchor="w", padx=8, pady=(0, 8))

        frm_log = ttk.LabelFrame(self.root, text="Log")
        frm_log.pack(fill="both", expand=True, padx=10, pady=8)

        self.txt_log = tk.Text(frm_log, wrap="word")
        self.txt_log.pack(fill="both", expand=True, padx=8, pady=8)

    def log(self, msg):
        self.txt_log.insert("end", msg + "\n")
        self.txt_log.see("end")

    # ------------------------------------------
    # FUNÇÕES DO SERVIDOR
    # ------------------------------------------
    def start_server(self):
        host = self.ent_host_server.get().strip()
        porta = int(self.ent_port_server.get().strip())

        def server_thread():
            try:
                self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                self.server_socket.bind((host, porta))
                self.server_socket.listen(5)
                self.log(f"[SERVIDOR] Ouvindo em {host}:{porta}")

                while True:
                    conn, addr = self.server_socket.accept()
                    self.log(f"[NOVA CONEXÃO] {addr}")
                    threading.Thread(target=self.handle_client, args=(conn, addr), daemon=True).start()
            except Exception as e:
                self.log(f"[ERRO SERVIDOR] {e}")

        threading.Thread(target=server_thread, daemon=True).start()

    def list_local_mp3(self):
        return [f for f in os.listdir(PASTA_COMPARTILHADOS) if f.lower().endswith(".mp3")]

    def handle_client(self, conn, addr):
        try:
            while True:
                comando = conn.recv(BUFFER).decode().strip()
                if not comando:
                    break

                self.log(f"[COMANDO RECEBIDO] {addr}: {comando}")

                if comando == "LIST":
                    arquivos = self.list_local_mp3()
                    resposta = "\n".join(arquivos) if arquivos else "NENHUM_ARQUIVO"
                    conn.send(resposta.encode())

                elif comando.startswith("GET "):
                    nome = comando[4:].strip()
                    caminho = os.path.join(PASTA_COMPARTILHADOS, nome)

                    if os.path.exists(caminho):
                        tamanho = os.path.getsize(caminho)
                        conn.send(f"OK {tamanho}".encode())

                        confirm = conn.recv(BUFFER).decode().strip()
                        if confirm == "READY":
                            with open(caminho, "rb") as f:
                                while True:
                                    dados = f.read(BUFFER)
                                    if not dados:
                                        break
                                    conn.sendall(dados)
                            self.log(f"[ARQUIVO ENVIADO] {nome} para {addr}")
                    else:
                        conn.send("ERRO Arquivo não encontrado".encode())

                elif comando == "SAIR":
                    break

                else:
                    conn.send("ERRO Comando inválido".encode())

        except Exception as e:
            self.log(f"[ERRO CLIENTE] {addr}: {e}")
        finally:
            conn.close()
            self.log(f"[DESCONECTADO] {addr}")

    # ------------------------------------------
    # FUNÇÕES DO CLIENTE
    # ------------------------------------------
    def connect_peer(self):
        try:
            host = self.ent_host_client.get().strip()
            porta = int(self.ent_port_client.get().strip())

            self.client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.client_socket.connect((host, porta))
            self.connected = True
            self.log(f"[CONECTADO] ao peer {host}:{porta}")
            messagebox.showinfo("Conexão", "Conectado com sucesso.")
        except Exception as e:
            self.log(f"[ERRO CONEXÃO] {e}")
            messagebox.showerror("Erro", str(e))

    def list_remote_files(self):
        if not self.connected:
            messagebox.showwarning("Aviso", "Conecte primeiro a um peer.")
            return

        try:
            self.client_socket.send("LIST".encode())
            resposta = self.client_socket.recv(BUFFER).decode()

            self.listbox.delete(0, "end")
            if resposta.strip() == "NENHUM_ARQUIVO":
                self.log("[LISTA] Nenhum arquivo remoto encontrado.")
                return

            for linha in resposta.split("\n"):
                if linha.strip():
                    self.listbox.insert("end", linha.strip())

            self.log("[LISTA] Arquivos remotos atualizados.")
        except Exception as e:
            self.log(f"[ERRO LISTA] {e}")
            messagebox.showerror("Erro", str(e))

    def download_selected(self):
        if not self.connected:
            messagebox.showwarning("Aviso", "Conecte primeiro a um peer.")
            return

        sel = self.listbox.curselection()
        if not sel:
            messagebox.showwarning("Aviso", "Selecione um arquivo.")
            return

        nome = self.listbox.get(sel[0])

        def download_thread():
            try:
                self.client_socket.send(f"GET {nome}".encode())
                resposta = self.client_socket.recv(BUFFER).decode()

                if not resposta.startswith("OK"):
                    self.log(f"[ERRO DOWNLOAD] {resposta}")
                    messagebox.showerror("Erro", resposta)
                    return

                tamanho = int(resposta.split()[1])
                self.client_socket.send("READY".encode())

                destino = os.path.join(PASTA_RECEBIDOS, nome)
                recebido = 0

                with open(destino, "wb") as f:
                    while recebido < tamanho:
                        dados = self.client_socket.recv(BUFFER)
                        if not dados:
                            break
                        f.write(dados)
                        recebido += len(dados)

                        porcentagem = (recebido / tamanho) * 100 if tamanho > 0 else 0
                        self.root.after(0, self.update_progress, porcentagem, recebido, tamanho)

                self.log(f"[DOWNLOAD CONCLUÍDO] {destino}")
                messagebox.showinfo("Sucesso", f"Arquivo salvo em:\n{destino}")

            except Exception as e:
                self.log(f"[ERRO DOWNLOAD] {e}")
                messagebox.showerror("Erro", str(e))

        threading.Thread(target=download_thread, daemon=True).start()

    def update_progress(self, pct, recebido, tamanho):
        self.progress["value"] = pct
        self.lbl_progress.config(text=f"Baixando... {pct:.1f}% ({recebido}/{tamanho} bytes)")


if __name__ == "__main__":
    root = tk.Tk()
    app = P2PGUI(root)
    root.mainloop()
