import os
import time
import requests
import base64

# Configurações do Repositório e Autenticação
TOKEN = "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"
OWNER = "brunnojob"
REPO = "android-offshore-field-console"
BASE_BRANCH = "main"

HEADERS = {
    "Authorization": f"Bearer {TOKEN}",
    "Accept": "application/vnd.github.v3+json"
}

API_URL = f"https://api.github.com/repos/{OWNER}/{REPO}"

def get_latest_commit_sha():
    url = f"{API_URL}/git/ref/heads/{BASE_BRANCH}"
    r = requests.get(url, headers=HEADERS)
    if r.status_code != 200:
        raise Exception(f"Erro ao obter a branch base: {r.text}")
    return r.json()["object"]["sha"]

def run_automation(start_pr=17, total_prs=128):
    print(f"A iniciar automação dos PRs #{start_pr} até ao #{total_prs} no repositório {OWNER}/{REPO}...")
    
    for i in range(start_pr, total_prs + 1):
        branch_name = f"bot-pr-level3-{i}-{int(time.time())}"
        
        try:
            # 1. Obter o SHA do último commit da branch principal
            base_sha = get_latest_commit_sha()
            
            # 2. Criar uma nova branch
            ref_url = f"{API_URL}/git/refs"
            ref_data = {
                "ref": f"refs/heads/{branch_name}",
                "sha": base_sha
            }
            res = requests.post(ref_url, json=ref_data, headers=HEADERS)
            if res.status_code != 201:
                print(f"[Erro] Falha ao criar branch {branch_name}: {res.text}")
                continue
                
            # 3. Criar ou atualizar o ficheiro de registo
            file_url = f"{API_URL}/contents/pr-log.txt"
            file_get = requests.get(file_url, headers=HEADERS)
            file_sha = file_get.json().get("sha") if file_get.status_code == 200 else None
            
            content_encoded = base64.b64encode(f"Pull Shark Level 3 iteration {i}\n".encode()).decode()
            
            commit_data = {
                "message": f"chore: pull shark level 3 iteration {i}",
                "content": content_encoded,
                "branch": branch_name
            }
            if file_sha:
                commit_data["sha"] = file_sha
                
            put_file = requests.put(file_url, json=commit_data, headers=HEADERS)
            if put_file.status_code not in [200, 201]:
                print(f"[Erro] Falha ao atualizar ficheiro no PR {i}: {put_file.text}")
                continue
                
            # 4. Criar o Pull Request
            pr_url = f"{API_URL}/pulls"
            pr_data = {
                "title": f"Automated PR Level 3 #{i}",
                "head": branch_name,
                "base": BASE_BRANCH,
                "body": f"Batch automation for Pull Shark Level 3 badge - item {i}"
            }
            pr_res = requests.post(pr_url, json=pr_data, headers=HEADERS)
            if pr_res.status_code != 201:
                print(f"[Erro] Falha ao criar PR {i}: {pr_res.text}")
                continue
                
            pr_number = pr_res.json()["number"]
            
            # 5. Fazer o Merge do Pull Request imediatamente
            merge_url = f"{API_URL}/pulls/{pr_number}/merge"
            merge_data = {"commit_title": f"Merge pull request #{pr_number} by automation"}
            merge_res = requests.put(merge_url, json=merge_data, headers=HEADERS)
            
            if merge_res.status_code == 200:
                print(f"[Sucesso] PR #{pr_number} criado e fundido com sucesso!")
            else:
                print(f"[Aviso] PR #{pr_number} criado mas falhou o merge: {merge_res.text}")
                
            # Pausa de 4 segundos para dar tempo ao GitHub de processar cada ciclo sem conflitos
            time.sleep(4)
            
        except Exception as e:
            print(f"Ocorreu uma exceção na iteração {i}: {e}")
            time.sleep(5)

if __name__ == "__main__":
    run_automation(start_pr=17, total_prs=128)