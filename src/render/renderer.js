// Scene, camera rig and lighting. The camera is a GTA-style angled top-down
// follow cam: fixed world orientation (no yaw — you always read the map the
// same way), smoothly tracking the local player with a little aim lead.

import * as THREE from 'three';

export function createRenderer(canvas, { touch, theme }) {
  const renderer = new THREE.WebGLRenderer({
    canvas,
    antialias: !touch,
    powerPreference: 'high-performance',
  });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, touch ? 1.75 : 2));
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;

  const scene = new THREE.Scene();
  const sky = new THREE.Color(theme.sky);
  scene.background = sky;
  scene.fog = new THREE.Fog(sky, 62, 140);

  const camera = new THREE.PerspectiveCamera(50, 1, 0.1, 300);
  const baseOffset = new THREE.Vector3(0, 13.2, 8.8);
  let offsetScale = 1;
  const target = new THREE.Vector3(0, 0, 0);
  const desired = new THREE.Vector3(0, 0, 0);

  // key light with shadows sized to the arena
  const sun = new THREE.DirectionalLight('#fff3e0', 2.0);
  sun.position.set(14, 26, 10);
  sun.castShadow = true;
  sun.shadow.mapSize.set(touch ? 1024 : 2048, touch ? 1024 : 2048);
  const sc = sun.shadow.camera;
  sc.left = -28; sc.right = 28; sc.top = 24; sc.bottom = -24;
  sc.near = 4; sc.far = 70;
  sun.shadow.bias = -0.0004;
  scene.add(sun);
  scene.add(new THREE.HemisphereLight('#b9d2ff', '#2a2f3a', 0.85));
  const rim = new THREE.DirectionalLight('#5f8cff', 0.5); // cool rim from behind
  rim.position.set(-10, 8, -16);
  scene.add(rim);

  function resize() {
    const w = canvas.clientWidth || window.innerWidth;
    const h = canvas.clientHeight || window.innerHeight;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    // portrait phones pull the camera up/back so the lane ahead stays visible
    offsetScale = Math.min(1.9, Math.max(1, Math.sqrt(1.55 / camera.aspect)));
    camera.updateProjectionMatrix();
  }
  window.addEventListener('resize', resize);
  resize();

  const raycaster = new THREE.Raycaster();
  const groundPlane = new THREE.Plane(new THREE.Vector3(0, 1, 0), 0);
  const ndc = new THREE.Vector2();
  const hit = new THREE.Vector3();

  return {
    three: renderer,
    scene,
    camera,

    // Convert a client-pixel position to the point it aims at on the floor.
    screenToGround(clientX, clientY) {
      ndc.set((clientX / canvas.clientWidth) * 2 - 1, -(clientY / canvas.clientHeight) * 2 + 1);
      raycaster.setFromCamera(ndc, camera);
      return raycaster.ray.intersectPlane(groundPlane, hit) ? { x: hit.x, z: hit.z } : null;
    },

    follow(px, pz, leadX, leadZ, dt, snap = false) {
      desired.set(px + leadX, 0, pz + leadZ);
      if (snap) target.copy(desired);
      else target.lerp(desired, 1 - Math.exp(-6 * dt));
    },

    render(shake = 0) {
      camera.position.set(
        target.x + baseOffset.x * offsetScale + (Math.random() - 0.5) * shake,
        target.y + baseOffset.y * offsetScale + (Math.random() - 0.5) * shake * 0.6,
        target.z + baseOffset.z * offsetScale + (Math.random() - 0.5) * shake,
      );
      camera.lookAt(target.x, target.y, target.z - 1.5);
      renderer.render(scene, camera);
    },

    dispose() {
      window.removeEventListener('resize', resize);
      scene.traverse((o) => {
        o.geometry?.dispose?.();
        const mats = Array.isArray(o.material) ? o.material : o.material ? [o.material] : [];
        for (const m of mats) {
          m.map?.dispose?.();
          m.dispose?.();
        }
      });
      renderer.dispose();
    },
  };
}
