import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// GitHub Pages serves this project at https://<user>.github.io/5/, so the
// build needs that sub-path as its base. Local dev keeps root ("/").
export default defineConfig({
  plugins: [react()],
  base: process.env.GITHUB_ACTIONS ? "/HighFive/" : "/",
  server: {
    // 로컬 백엔드(기본 8080)로 요청을 그대로 넘긴다 - 브라우저 입장에서는 같은 오리진이라
    // 백엔드에 별도 CORS 설정을 추가할 필요가 없다. 백엔드 API 경로가 전부 /api 밑에 있지 않아서
    // (AdminCouponController: /admin/coupons, CouponController: /coupons/{couponId}/issue 등)
    // 그것도 같이 넘긴다 - 이 프런트에 클라이언트 라우팅이 없어서 겹칠 일이 없다.
    proxy: {
      "/api": "http://localhost:8080",
      "/admin": "http://localhost:8080",
      "/coupons": "http://localhost:8080",
      "^/\\d+(/.*)?$": "http://localhost:8080",
    },
  },
});
