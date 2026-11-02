/**
 * Las pruebas leen hojas de estilo como texto con `import css from './x.css' with { loader: 'text' }`,
 * que el compilador de Angular (esbuild) resuelve. Solo aplica a las pruebas: la librería no importa CSS.
 */
declare module '*.css' {
  const content: string;
  export default content;
}
