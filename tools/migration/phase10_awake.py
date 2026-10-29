"""Keep the measurement host active only while the verified phase10 runner lives.
Thread-scoped Windows execution request; no power-plan/registry setting is changed.
No DISPLAY_REQUIRED flag. Manual sleep/shutdown still belongs to the user.
"""
import argparse,ctypes,json
from ctypes import wintypes
import stage9 as env

def main(pid):
    env.stage3.safety();assert pid>0
    line=env.command(['powershell','-NoProfile','-Command',f"(Get-CimInstance Win32_Process -Filter 'ProcessId={pid}').CommandLine | ConvertTo-Json -Compress"]).decode().strip()
    normalized=json.loads(line).replace('\\','/')
    assert any(name in normalized for name in ('tools/migration/phase10.py','tools/migration/phase10_quick.py'))
    kernel=ctypes.WinDLL('kernel32',use_last_error=True)
    kernel.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD];kernel.OpenProcess.restype=wintypes.HANDLE
    kernel.WaitForSingleObject.argtypes=[wintypes.HANDLE,wintypes.DWORD];kernel.CloseHandle.argtypes=[wintypes.HANDLE]
    kernel.SetThreadExecutionState.argtypes=[wintypes.DWORD];kernel.SetThreadExecutionState.restype=wintypes.DWORD
    handle=kernel.OpenProcess(0x100000,False,pid);assert handle
    try:
        assert kernel.SetThreadExecutionState(0x80000001)
        print('Transient system-awake request active for verified runner '+str(pid),flush=True)
        assert kernel.WaitForSingleObject(handle,0xFFFFFFFF)==0
    finally:kernel.SetThreadExecutionState(0x80000000);kernel.CloseHandle(handle)
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--pid',type=int,required=True);main(p.parse_args().pid)
