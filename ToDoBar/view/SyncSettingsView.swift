import SwiftUI
import CoreImage.CIFilterBuiltins

struct SyncSettingsView: View {
    @ObservedObject var server: SyncServer

    var body: some View {
        VStack(spacing: 16) {
            Text("局域网收集箱").font(.title2)
            Text("状态：\(server.status)").font(.caption).foregroundStyle(.secondary)
            if let name = server.pairedDeviceName {
                Image(systemName: "iphone.and.arrow.forward").font(.system(size: 52))
                Text("已绑定：\(name)")
                Text("手机与此 Mac 回到配对时的 Wi‑Fi 后，可自动发送待办。Mac 需要保持唤醒且 ToDoBar Sync 正在运行。")
                    .multilineTextAlignment(.center).foregroundStyle(.secondary)
                Button("撤销绑定", role: .destructive) { server.revoke() }
            } else if let value = server.qrString, let image = qrImage(value) {
                Image(nsImage: image).interpolation(.none).resizable().frame(width: 280, height: 280)
                Text("用“TodoBar 收集箱”扫码。二维码含一次性配对密钥，请勿分享。")
                    .multilineTextAlignment(.center).foregroundStyle(.secondary)
            }
            Spacer()
        }.padding(24).frame(minWidth: 400, minHeight: 500)
    }

    private func qrImage(_ value: String) -> NSImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(value.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage?.transformed(by: CGAffineTransform(scaleX: 10, y: 10)) else { return nil }
        let rep = NSCIImageRep(ciImage: output)
        let image = NSImage(size: rep.size); image.addRepresentation(rep); return image
    }
}
